# Plan de refactor: Domain Driven Design

Estado: **en ejecución**
Rama: `feature/multitenant-security`
Alcance acordado: fases 1–6 completas · lenguaje ubicuo unificado en **inglés** · bus de eventos **en memoria**

---

## 1. Diagnóstico del estado actual

| Síntoma | Ubicación | Problema DDD |
|---|---|---|
| Modelo anémico | `domain/model/*` (`@Data @Builder`, solo getters/setters) | Entidades sin invariantes ni comportamiento |
| Lógica de dominio en infraestructura | `SlotReservationJpaAdapter.speichern()`, `zeile.setState(...)` | La máquina de estados `PENDING → CONFIRMED / RELEASED` vive en el adapter JPA |
| Lógica de dominio en la capa de aplicación | `AppointmentService#createAppointment` | Orquestación + reglas + mapeo + notificación en un mismo método |
| Dominio contaminado | `domain/command/CreateAppointmentRequest` usa `@JsonProperty` y `jakarta.validation`, y se envía **tal cual** a Microsoft Graph | Sin capa anticorrupción (ACL); el modelo externo dicta el interno |
| Framework en la capa de aplicación | `ResponseStatusException` en `resolveAgency` / `resolveStaffIds` | Concern web dentro del caso de uso |
| Ausencia de value objects | `String businessId/serviceId/staffId`, `Instant start/end` sueltos | *Primitive obsession*; la regla de solape está duplicada en tres sitios |
| Ausencia de eventos de dominio | `sendNotifications()` con `try/catch` que traga la excepción | Acoplamiento directo caso de uso → email |
| Lenguaje ubicuo roto | Mezcla de alemán, español e inglés (`freigebenNachTerminId`, `verwaisteFinden`, `kalenderAnsichtAbrufen`, `mitarbeiterIds`) | Sin *ubiquitous language* compartido |

### La regla de solape, hoy duplicada

La misma invariante de negocio (intervalos semiabiertos `[start, end)`) está escrita tres veces
de forma independiente, y por tanto puede divergir:

1. `SlotRecoveryService.ueberlappt(...)` — comparación en memoria
2. `SlotReservationRepository.countOverlappingReservations(...)` — en SQL
3. `SlotRequest` (constructor compacto) — solo valida `end > start`

Un único value object `TimeWindow` pasa a ser la fuente de verdad.

---

## 2. Modelo táctico objetivo

**Bounded context:** `scheduling`

### Agregado 1 — `Booking` (raíz)

- Identidad: `BookingId`
- Entidades hijas: `SlotReservation` (una por *staff member* asignado)
- Invariante interna: **todo o nada** — o se reservan todos los slots, o ninguno
- Comportamiento: `confirm(AppointmentId)`, `cancel()`, `rescheduleTo(TimeWindow)`, `release()`
- Transiciones guardadas: confirmar un booking `RELEASED` lanza `IllegalBookingStateException`

### Agregado 2 — `Agency` (raíz, sustituye a `AgencyMapping`)

- Entidad hija: `StaffMember` (sustituye a `StaffMapping`)
- Comportamiento: `resolveStaff(List<StaffName>) : List<StaffMemberId>`
  — absorbe el bucle de resolución que hoy vive en `AppointmentService`
- Lanza `StaffMemberNotFoundException` (dominio), no `ResponseStatusException` (web)

### Value objects

`BookingId`, `AgencyId`, `BusinessId`, `ServiceId`, `StaffMemberId`, `AppointmentId`,
`TimeWindow` (con `overlaps()`, intervalo semiabierto), `CustomerContact`, `AgencyName`.

### Invariante entre agregados

El no-solape entre bookings **distintos** cruza fronteras de agregado y por tanto no puede
garantizarse en memoria. Se sigue aplicando en el adapter mediante el *pessimistic write lock*
sobre la fila del staff member, pero la regla se expresa en el dominio como
`SlotAvailabilityPolicy`. Queda documentado de forma explícita: **consistencia garantizada por
la base de datos, no por el agregado.**

---

## 3. Eventos de dominio y bus en memoria

```
domain/event/DomainEvent            (eventId, occurredOn, aggregateId)
domain/AggregateRoot                registerEvent(), pullEvents()  ← drena y limpia
domain/port/out/DomainEventPublisher
infrastructure/adapter/out/event/InMemoryEventBus
```

**Eventos:** `SlotsReserved`, `BookingConfirmed`, `BookingCancelled`, `BookingRescheduled`,
`SlotReleased`, `OrphanedReservationRecovered`.

**Decisión de diseño — registro propio, no `ApplicationEventPublisher` de Spring.**
El bus mantiene un `Map<Class<? extends DomainEvent>, List<DomainEventHandler<?>>>` poblado
desde el `ApplicationContext` al arrancar. Motivo: el dominio queda libre de Spring, los
handlers se testean sin contexto, y sustituir el bus por un broker real (Kafka, Service Bus)
afecta solo al adapter.

**Semántica de entrega:** dispatch síncrono, con `try/catch` por handler — un handler que falla
se registra en el log pero no tumba la reserva. Los eventos se publican **después** del commit
del repositorio, nunca dentro de la transacción.

**Handler:** `SendBookingConfirmationHandler` (capa de aplicación) escucha `BookingConfirmed`
y llama a `BookingNotificationPort`. Elimina `AppointmentService.sendNotifications()`.

---

## 4. Fases

| # | Fase | Entregable | Estado |
|---|---|---|---|
| 0 | Baseline | Suite verde tras arreglar 3 bloqueos de entorno en `pom.xml` | ✅ |
| 1 | Value objects, excepciones de dominio, lenguaje ubicuo | `TimeWindow` unifica las tres definiciones de solape; API del puerto en inglés | ✅ |
| 2 | Agregado `Agency` + `StaffMember` | `ResponseStatusException` fuera de la capa de aplicación | ✅ |
| 3 | Agregado `Booking` + `SlotReservation` de dominio | Máquina de estados fuera del adapter; migración `V3__booking_id.sql` | ✅ |
| 4 | Eventos + bus en memoria | Notificación desacoplada por evento | ✅ |
| 5 | Casos de uso + ACL de Graph | `AppointmentDraft` + `AppointmentCalendarPort` + traductor en infra | ✅ |
| 6 | Tests + ArchUnit ampliado | 44 tests sin Spring; 6 reglas nuevas | ✅ |
| 7 | Barrida alemán → inglés | Todos los identificadores traducidos | 🟡 falta la prosa |

### La ACL, tal como quedó

La escritura pasa por el dominio: `AppointmentDraft` describe la intención y
`GraphAppointmentAdapter` es la única clase que sabe qué JSON espera Graph.

El draft lleva la **zona del cliente** junto a la ventana UTC. La ventana es UTC porque es la
única forma en que dos reservas se pueden comparar por solape, pero un cliente reserva
«10:00 en Berlín» y eso es lo que el calendario debe mostrar. Renderizar el instante de vuelta
a esa zona reproduce la hora de pared que mandó el cliente, en vez de reescribir en silencio
todas las citas a UTC al salir.

Las **lecturas se quedan como estaban**, devolviendo `BookingAppointmentDto` directo de Graph.
Pasarlas por tipos de dominio obligaría a remodelar cada campo que Graph devuelve, y todo lo
no modelado desaparecería de las respuestas de esta API. Los comandos pasan por el modelo;
las consultas no lo necesitan.

`GraphApiRequest` sigue existiendo con forma de HTTP para el CRUD administrativo de negocios,
servicios y personal, donde el sistema es un simple pasamanos — pero ya vive en
`application/port/out`, una capa a la que sí le corresponde conocer el transporte.

### Lo que queda

**Fase 7 — la prosa.** Los identificadores están todos en inglés; los comentarios y javadoc
siguen en alemán en la infraestructura, los DTOs y los tests. Los archivos reescritos durante
el refactor ya llevan prosa en inglés.

### Nombres que se quedan en alemán a propósito

`GlobalExceptionHandler.Fehlerantwort` y sus componentes `nachricht` y `zeitstempel`.
Jackson serializa un record por los nombres de sus componentes, así que este tipo emite
`{"status":…,"nachricht":…,"zeitstempel":…}` — el cuerpo de error que ya parsea todo cliente
existente. Renombrarlos rompe a esos clientes en silencio: es una decisión de versión de API,
no un refactor.

### Bloqueos de entorno resueltos en la fase 0

Preexistentes, no causados por el refactor. La suite corría contra clases obsoletas de
`target/`, lo que los ocultaba:

| Problema | Arreglo |
|---|---|
| ByteBuddy 1.14.x no conoce bytecode 68 → Mockito tumbaba el contexto Spring | `byte-buddy.version` → 1.17.5 |
| Lombok 1.18.30 no soporta JDK 24 | `lombok.version` → 1.18.46 |
| JDK 23+ no ejecuta procesadores de anotaciones implícitos | `annotationProcessorPaths` explícito |

### Reglas ArchUnit

Activas y en verde:

- `..domain..` no depende de `..application..` ni `..infrastructure..`
- `..application..` no depende de `..infrastructure..`
- adaptadores de entrada no dependen de los de salida
- `..domain..` no depende de `org.springframework..`
- `..domain..` no depende de `jakarta.persistence..` ni `org.hibernate..`
- `..domain.model..`, `..domain.event..`, `..domain.port..` no dependen de
  `com.fasterxml.jackson..` ni de `jakarta.validation..`

**Nota sobre el alcance de las dos últimas.** Están limitadas a `model`, `event` y `port`
en lugar de a `..domain..` entero porque `domain/command/*` (4 clases) sigue llevando
`@JsonProperty` y `jakarta.validation`. Ampliarlas a todo el dominio es el criterio de
aceptación de la fase 5.

---

## 5. Fuera de alcance

- *Transactional outbox* — el bus en memoria pierde eventos si el proceso cae entre el commit
  y el dispatch. Aceptado conscientemente: las notificaciones no son críticas. Se anota como
  deuda técnica para cuando exista un broker.
- Event sourcing.
- CQRS con read models separados.

---

## 6. Relación con documentos previos

Este plan **preserva** el algoritmo de colisiones descrito en `PLAN-COLISION-RESERVAS.md`
(lock pesimista + `countOverlappingReservations`). No lo sustituye: lo reubica detrás de una
frontera de agregado y le da un nombre de dominio.
