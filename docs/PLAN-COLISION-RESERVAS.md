# Plan de implementación: prevención de colisiones de reservas (double-booking)

**Fecha:** 2026-07-29
**Rama de análisis:** `feature/init-webclient`
**Estado:** parcialmente implementado

| Fase | Estado |
|------|--------|
| 0 — Test que reproduce el fallo | ✅ hecho (`AppointmentConcurrencyTest`, `SlotReservierungTest`) |
| 1 — Idempotencia (`Idempotency-Key`) | ⬜ pendiente |
| 2 — Reserva atómica en PostgreSQL | ✅ hecho (`slot_reservation` + `EXCLUDE`, `SlotReservationJpaAdapter`) |
| 3 — Precomprobación de disponibilidad | ⬜ pendiente |
| 4 — Job de reconciliación | ⬜ pendiente |
| 5 — Métricas y alertas | ⬜ pendiente |
| 6 — Thread-safety de `GraphAuthService` | ⬜ pendiente |
| 7 — Bajar `responseTimeout` a 10 s | ⬜ pendiente (requiere fase 1 antes) |

### Estado por escenario de fallo (§3)

| # | Escenario | Estado |
|---|-----------|--------|
| A | Dos clientes, mismo slot, en paralelo | ✅ resuelto |
| B | Doble clic / retry del navegador | ✅ sin duplicado (2º recibe 409) |
| C | Retry automático tras timeout | ⚠️ sin duplicado, pero el cliente recibe 409 sin saber que su cita sí existe → requiere fase 1 |
| D | Slot capacidad 1, N réplicas | ✅ el árbitro es la BD, compartido |
| E | Reprogramación a slot ocupado | ✅ resuelto |

> **§2.5 implementado.** La liberación distingue respuesta definitiva de Graph
> (libera) de ausencia de respuesta (**no** libera, deja `PENDING`), y el job
> `SlotWiederherstellungService` consulta `/calendarView` antes de decidir:
> cita encontrada → `CONFIRMED`; no encontrada → `RELEASED`; Graph caído →
> sin cambios. Cron por defecto: cada 5 min.
>
> **Pendiente operativo:** con ≥2 réplicas, todas ejecutan el job. Es inocuo
> (idempotente, verifica antes de decidir) pero gasta cuota de Graph. Con más
> de dos instancias, añadir elección por lock (ShedLock).

---

## 1. Veredicto

**Sí, el sistema actual permite colisiones.** Dos peticiones concurrentes con el mismo
`serviceId` + `staffMemberIds` + `startDateTime/endDateTime` y clientes distintos se
crearán **ambas** como citas válidas en Microsoft Bookings.

No es un riesgo teórico: es el comportamiento garantizado del código tal como está.

---

## 2. Evidencia en el código

### 2.1 La creación no valida nada

`src/main/java/com/booking/azure/service/AppointmentService.java:102-106`

```java
@Override
public BookingAppointmentDto terminErstellen(String betriebId, CreateAppointmentRequest anfrage) {
    log.info("Neuer Termin wird erstellt in Betrieb {}, Dienst: {}", betriebId, anfrage.getServiceId());
    return graphApiAnfrage.post(terminePfad(betriebId), anfrage, BookingAppointmentDto.class);
}
```

Es un *passthrough* puro: request entra → POST a Graph → response sale. Sin lectura previa
de disponibilidad, sin lock, sin transacción, sin clave de idempotencia.

### 2.2 Existe comprobación de disponibilidad, pero nadie la llama

`StaffMemberService.mitarbeiterVerfuegbarkeitAbrufen()`
(`src/main/java/com/booking/azure/service/StaffMemberService.java:126-136`) invoca
`POST /getStaffAvailability`. Grep del proyecto: sólo la usa su propio controlador
(`StaffMemberController`). **`AppointmentService` no la consulta nunca.**

### 2.3 El endpoint de Graph usado no protege contra solapamiento

El código escribe en:

```
POST /solutions/bookingBusinesses/{id}/appointments
```

Ese es el endpoint **administrativo**. Microsoft lo diseña para que el personal/admin pueda
forzar citas fuera de horario o sobre-reservar deliberadamente. **No aplica las validaciones
de disponibilidad** que sí aplica la página de auto-reserva del cliente
(`https://outlook.office.com/book/...`, referenciada en `application.yml` como
`buchung.buchungs-basis-url`).

Consecuencia: Graph aceptará las dos citas solapadas y devolverá `201 Created` a ambas.

### 2.4 Sin estado propio, sin control de concurrencia

- `pom.xml`: no hay JPA, no hay driver JDBC, no hay Redis. Cero persistencia local.
- No hay `@Transactional`, ni `synchronized`, ni lock distribuido en ningún servicio.
- `GraphApiClient` no envía `If-Match` / ETag en ninguna operación
  (`GraphApiClient.java:74-94` — POST sin cabecera condicional). Graph tampoco expone
  concurrencia optimista para `bookingAppointment`.
- La app es *stateless*: al desplegar en Azure con ≥2 réplicas, incluso un `synchronized`
  en memoria sería inútil.

### 2.5 Ventana temporal real

Aunque se añadiera un `getStaffAvailability` antes del POST, quedaría un **TOCTOU**
(Time-Of-Check-To-Time-Of-Use): dos hilos leen "libre", ambos escriben. Con latencias
típicas de Graph (100–400 ms por llamada) la ventana es de **cientos de milisegundos** —
enorme para tráfico web real.

### 2.6 El timeout actual fabrica duplicados

`WebClientConfig.java:38` fija `responseTimeout(Duration.ofSeconds(30))`.

Un timeout **no es un mecanismo de exclusión mutua**, es un límite de espera. No impide que
dos peticiones escriban: efecto nulo sobre colisiones de slot.

Peor: es la causa directa del escenario C. Graph tarda 31 s → el cliente aborta → reintenta
→ pero el primer POST **sí llegó** y creó la cita. Dos citas idénticas.

Problema añadido de capacidad: durante esos 30 s un hilo de Tomcat queda bloqueado en
`.block()` (`GraphApiClient.java:89`). Con el pool por defecto (200 hilos), 200 peticiones
lentas concurrentes agotan el servidor completo.

Acción: bajar a ~10 s. **Pero no desplegar esa bajada sin la Fase 1 (idempotencia)** — más
timeouts significan más reintentos, y sin idempotencia eso multiplica los duplicados.

### 2.7 Hallazgo colateral (no bloqueante pero relacionado)

`GraphAuthService.getAccessToken()` (`GraphAuthService.java:46-72`) lee y escribe
`gecachtesToken` / `tokenAblaufzeit` sin sincronización ni `volatile`, y
`getClientApplication()` hace lazy-init no seguro. Bajo carga concurrente: renovaciones
duplicadas de token y posible lectura de token obsoleto. Arreglar junto con este trabajo.

---

## 3. Escenarios de fallo

| # | Escenario | Resultado hoy |
|---|-----------|---------------|
| A | Dos clientes distintos, mismo slot, mismo staff, en paralelo | 2 citas solapadas creadas |
| B | Un cliente hace doble clic / retry del navegador | 2 citas duplicadas idénticas |
| C | Retry automático del cliente HTTP tras timeout (la 1ª sí llegó) | 2 citas duplicadas |
| D | Slot capacidad 1, N réplicas de la app | Todas aceptan |
| E | Reprogramación (`PUT`) hacia un slot ya ocupado | Aceptado sin validar |

Escenarios B y C son **más frecuentes** que A en producción y se resuelven con
idempotencia, que es más barata que el locking.

---

## 4. Estrategia propuesta

Defensa en profundidad, cuatro capas. Microsoft Graph **no puede ser la fuente de verdad**
para exclusión mutua porque no ofrece primitivas atómicas. Por tanto el sistema necesita
**estado propio** que actúe de árbitro.

```
Cliente
  │
  ├─ Capa 1: Idempotencia   (mata duplicados por retry/doble-clic)
  ├─ Capa 2: Reserva atómica en BD  (UNIQUE constraint = árbitro real)
  ├─ Capa 3: Escritura en Graph     (sólo el ganador del lock llega aquí)
  └─ Capa 4: Reconciliación         (red de seguridad: detecta y corrige derivas)
```

### Decisión recomendada: tabla de reservas con `UNIQUE` en PostgreSQL

Preferible a un lock distribuido en Redis porque:

- La `UNIQUE constraint` es atómica **por definición**, sin TTL que ajustar ni riesgo de
  liberar un lock ajeno.
- Deja rastro auditable de qué slot se ocupó, cuándo y por quién.
- Sirve simultáneamente de tabla de idempotencia y de reconciliación.
- Azure Database for PostgreSQL Flexible Server ya es un servicio gestionado disponible.

Redis (`SET NX PX`) es alternativa válida si ya existe Redis en la plataforma y no se quiere
introducir una BD; documentada en §6.

---

## 5. Plan de implementación por fases

### Fase 0 — Reproducir el fallo (obligatorio antes de tocar nada)

**Objetivo:** test que falle hoy y pase al final.

- Test de integración con `WireMock` simulando Graph.
- Lanzar N=20 `POST /api/businesses/{id}/appointments` concurrentes con payload idéntico
  salvo `customers`, vía `ExecutorService` + `CountDownLatch`.
- Aserción: WireMock recibe exactamente **1** POST a `/appointments`; las otras 19
  respuestas son `409 Conflict`.
- Añadir `wiremock-jre8-standalone` y `awaitility` como dependencias `test`.

**Entregable:** `src/test/java/com/booking/azure/service/AppointmentConcurrencyTest.java`
(rojo).

---

### Fase 1 — Clave de idempotencia

**Objetivo:** matar los escenarios B y C, que son la mayoría del volumen de duplicados.

1. Aceptar cabecera `Idempotency-Key` (UUID generado por el cliente) en
   `AppointmentController.terminErstellen`.
2. Si falta, derivar una clave determinista:
   `sha256(businessId | serviceId | startUtc | endUtc | staffIdsOrdenados | emailClienteNormalizado)`.
3. Tabla `appointment_idempotency`:

```sql
CREATE TABLE appointment_idempotency (
    idempotency_key   VARCHAR(128) PRIMARY KEY,
    business_id       VARCHAR(255) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    graph_appointment_id VARCHAR(255),
    status            VARCHAR(20)  NOT NULL,   -- IN_PROGRESS | COMPLETED | FAILED
    response_body     JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ  NOT NULL
);
```

4. Semántica:
   - `COMPLETED` → devolver la respuesta cacheada, `200 OK` (no `201`).
   - `IN_PROGRESS` → `409 Conflict` con `Retry-After`.
   - Mismo `idempotency_key` pero `request_hash` distinto → `422 Unprocessable Entity`.
5. Job de purga: borrar filas con `expires_at < now()` (retención sugerida 24 h).

**Coste:** bajo. **Impacto:** alto.

---

### Fase 2 — Reserva atómica de slot (el núcleo)

**Objetivo:** matar los escenarios A, D y E.

#### 2.1 Esquema

```sql
CREATE TABLE slot_reservation (
    id                   BIGSERIAL PRIMARY KEY,
    business_id          VARCHAR(255) NOT NULL,
    service_id           VARCHAR(255) NOT NULL,
    staff_member_id      VARCHAR(255) NOT NULL,
    start_utc            TIMESTAMPTZ  NOT NULL,
    end_utc              TIMESTAMPTZ  NOT NULL,
    graph_appointment_id VARCHAR(255),
    state                VARCHAR(20)  NOT NULL,  -- PENDING | CONFIRMED | RELEASED
    idempotency_key      VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Árbitro: un único slot activo por (negocio, empleado, hora de inicio)
CREATE UNIQUE INDEX ux_slot_active
    ON slot_reservation (business_id, staff_member_id, start_utc)
    WHERE state IN ('PENDING', 'CONFIRMED');
```

#### 2.2 Solapamientos parciales y Bloqueo Pesimista (Adaptación para Oracle DB)

> [!NOTE]  
> **Adaptación a Oracle:** Oracle Database no soporta los operadores de rango de tiempo (`tstzrange`) ni los índices `EXCLUDE USING gist` detallados a continuación.  
> Por tanto, **el algoritmo de colisión ha sido implementado programáticamente en Java** (`SlotReservationJpaAdapter.speichern()`).
>
> **¿Cómo funciona el algoritmo actual?**
> 1. **Bloqueo Pesimista (`PESSIMISTIC_WRITE`)**: Antes de revisar los solapamientos, se bloquea la fila del trabajador (`StaffMapping`) mediante un `SELECT ... FOR UPDATE` a nivel de base de datos (`SpringDataStaffRepository.lockByMsStaffMemberId`).
> 2. **Comprobación (`Check`)**: Con el trabajador bloqueado de manera exclusiva, se cuenta cuántas reservas activas (estado `PENDING` o `CONFIRMED`) existen para ese trabajador que se solapen con las horas deseadas (`SlotReservationRepository.countOverlappingReservations`).
> 3. **Inserción o Rechazo**: Si existen colisiones, se lanza un `SlotConflictException` (`HTTP 409`). Si no, se inserta la reserva y se confirma la transacción.

(La siguiente documentación de PostgreSQL se mantiene por contexto histórico):

`start_utc` idéntico no cubre el caso 10:00–11:00 vs 10:30–11:30. Para eso hace falta una
exclusion constraint sobre rangos (Originalmente en Postgres):

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE slot_reservation
  ADD CONSTRAINT ex_slot_overlap
  EXCLUDE USING gist (
      business_id     WITH =,
      staff_member_id WITH =,
      tstzrange(start_utc, end_utc, '[)') WITH &&
  ) WHERE (state IN ('PENDING', 'CONFIRMED'));
```

Esto es lo que realmente se quiere. El índice único de 2.1 queda como alternativa simple si
el negocio garantiza slots de rejilla fija.

> **Nota sobre capacidad > 1:** si el servicio admite varios asistentes
> (`maximumAttendeesCount` en Graph), la exclusión estricta es incorrecta. En ese caso
> sustituir por columna `capacity` + `booked_count` y usar
> `UPDATE ... SET booked_count = booked_count + 1 WHERE booked_count < capacity`
> (atómico, sin lock explícito). Decidir con el negocio antes de codificar la Fase 2.

#### 2.3 Normalización de zona horaria — **crítico**

`DateTimeTimeZoneDto` transporta hora local + zona. Dos peticiones al mismo instante pueden
llegar como `2026-08-01T10:00 Europe/Berlin` y `2026-08-01T08:00 UTC`. Si se indexa el
string crudo, **la constraint no detecta la colisión**.

Regla: convertir **siempre** a `Instant` UTC antes de tocar la BD.

```java
private Instant zuInstant(DateTimeTimeZoneDto dto) {
    return LocalDateTime.parse(dto.getDateTime())
            .atZone(ZoneId.of(dto.getTimeZone()))
            .toInstant();
}
```

Añadir tests de esta conversión con al menos un caso en cambio de horario de verano.

#### 2.4 Flujo transaccional

```
1. INSERT slot_reservation (state = PENDING)
      └─ DataIntegrityViolationException  →  409 Conflict "slot ya reservado"
2. COMMIT   (el lock ya es visible para las demás réplicas)
3. POST a Graph  /solutions/bookingBusinesses/{id}/appointments
      ├─ 201 → UPDATE state = CONFIRMED, graph_appointment_id = <id>
      └─ error → UPDATE state = RELEASED   (compensación)
4. Devolver 201 con el DTO de la cita
```

**Importante:** el commit del paso 2 va **antes** de la llamada HTTP. Mantener una
transacción abierta durante una llamada de red externa agota el pool de conexiones bajo
carga.

#### 2.5 Reservas `PENDING` huérfanas — y por qué el TTL NO es el árbitro

Si la instancia muere entre los pasos 2 y 3, la fila queda `PENDING` para siempre y bloquea
el slot. Hace falta un `expires_at` y un job de recuperación.

**Punto crítico de diseño:** en este esquema el TTL **no participa en la corrección**. La
exclusión mutua la garantiza la `EXCLUDE` constraint, que es atómica. El TTL cubre un hueco
mucho más estrecho: recuperar filas huérfanas tras un crash. No confundir los dos papeles.

##### La liberación debe ser verificada, nunca ciega

Un job que borre por temporizador **causa el bug que pretende arreglar**:

```
t=0    A inserta PENDING, expires_at = t+90
t=0.1  A hace POST a Graph. Graph degradado, tarda 120 s
t=90   job borra la fila "caducada". Slot libre.
t=91   B reserva el slot, POST a Graph → 201
t=120  el POST de A también llega → 201
       Dos citas solapadas. En silencio.
```

La expiración de una fila **no cancela la operación HTTP en vuelo**. Solo hace que el
sistema crea que puede reasignar el slot mientras la primera escritura sigue viva. Es la
crítica clásica de Kleppmann a los locks basados en TTL, y aplica igual aquí.

Algoritmo correcto del job:

```
Para cada PENDING con expires_at < now():
    consultar Graph /calendarView acotado a ese slot
    ├─ existe la cita  →  UPDATE state = CONFIRMED, graph_appointment_id = <id>
    │                     (recuperar, NO liberar: la escritura sí ocurrió)
    └─ no existe       →  UPDATE state = RELEASED
```

##### Elección del valor de TTL

Regla: **> p99 de latencia de Graph + margen**. Graph en condiciones normales responde en
200–400 ms; degradado, varios segundos.

- **90 s** — recomendado. La ventana real que protege es de milisegundos; el margen es
  contra latencia de cola, no contra el caso normal.
- **15 s o menos** — demasiado justo. Dispara el camino de recuperación en cuanto Graph se
  degrada, sin necesidad.
- **15 min o más** — inventario congelado tras cada crash o despliegue.

Un TTL corto **no es la opción conservadora**. Con liberación verificada el riesgo queda
acotado igualmente, pero un TTL corto genera ruido y carga innecesaria de reconciliación.

#### 2.6 Cobertura de `PUT` y `DELETE`

- `terminAktualisieren` (reprogramación): liberar la reserva antigua y adquirir la nueva en
  la **misma transacción**. Es el escenario E.
- `terminStornieren`: pasar la reserva a `RELEASED` tras el DELETE en Graph, si no el slot
  queda bloqueado permanentemente.

---

### Fase 3 — Precomprobación de disponibilidad (UX, no seguridad)

Antes del paso 1 de §2.4, llamar a `MitarbeiterVerwaltung.mitarbeiterVerfuegbarkeitAbrufen`
y rechazar pronto con `409` + lista de slots alternativos.

**No sustituye a la Fase 2.** Es optimización de experiencia: evita el 99 % de los
conflictos con un mensaje útil, mientras la constraint cubre el 1 % restante que gana la
carrera. Implementarla **después** de la Fase 2, nunca en su lugar.

---

### Fase 4 — Reconciliación (red de seguridad)

Job `@Scheduled` (sugerido: cada 15 min):

1. `GET /calendarView` para las próximas 72 h de cada `businessId` activo.
2. Detectar solapamientos reales por empleado en la respuesta de Graph.
3. Contrastar contra `slot_reservation`.
4. Ante discrepancia:
   - Emitir métrica `booking.collision.detected` (contador) + log `ERROR` con ambos IDs.
   - **No cancelar automáticamente en la primera versión.** Alertar y que un humano decida
     — cancelar la cita de un cliente real por un bug de reconciliación es peor que el
     solapamiento.
   - Política de resolución automática (cancelar la de `createdDateTime` mayor, notificar al
     cliente) sólo tras un periodo de observación con datos.

Cubre: citas creadas fuera de esta API (portal de auto-reserva del cliente, admin de
Bookings, otra integración). Ninguna de esas pasa por la BD, así que la Fase 2 sola no las ve.

---

### Fase 5 — Observabilidad

Métricas Micrometer vía el Actuator ya presente:

| Métrica | Tipo | Significado |
|---------|------|-------------|
| `booking.reservation.conflict` | counter | Slots rechazados por la constraint |
| `booking.idempotency.hit` | counter | Retries deduplicados |
| `booking.reservation.orphaned` | counter | `PENDING` caducadas liberadas |
| `booking.collision.detected` | counter | Solapamientos hallados por reconciliación |
| `booking.graph.latency` | timer | Latencia por operación de Graph |

Alerta: `booking.collision.detected > 0` → página al equipo. Es un indicador de que algo se
escapa por un camino no controlado.

---

### Fase 6 — Corregir seguridad de hilos en `GraphAuthService`

Referencia §2.7. Cambio pequeño, mismo PR o el siguiente:

- `AtomicReference<TokenHolder>` con `{token, expiry}` como par inmutable, o
- Sincronizar `getAccessToken()` con doble comprobación y campos `volatile`, y
- Inicializar `ConfidentialClientApplication` como `@Bean` en `WebClientConfig` en lugar de
  lazy-init no seguro.

---

## 6. Los tres significados de "reservar el slot" / "poner un timeout"

Tres ideas distintas se confunden bajo el mismo nombre. Solo una es la solución.

| Idea | Qué hace | Veredicto |
|---|---|---|
| Timeout HTTP | Límite de espera del cliente | **No sirve.** No es exclusión mutua. Genera duplicados (§2.6) |
| Reserva técnica (`PENDING`) | Lock de milisegundos, invisible | **Sí.** Es la Fase 2 |
| Hold de negocio (10 min, visible) | Retiene inventario mientras el usuario rellena/paga | **Depende del flujo.** Ver §6.2 |

### 6.1 Alternativa a PostgreSQL: lock distribuido con Redis

Si no se quiere introducir PostgreSQL:

```java
String lockKey = "slot:" + businessId + ":" + staffId + ":" + startUtc.toEpochMilli();
Boolean adquirido = redis.opsForValue()
        .setIfAbsent(lockKey, instanceId, Duration.ofSeconds(30));
if (!Boolean.TRUE.equals(adquirido)) {
    throw new SlotConflictException(...);
}
try {
    // POST a Graph
} finally {
    // liberar sólo si el valor sigue siendo instanceId (script Lua compare-and-delete)
}
```

**Contras frente a la BD:**

- **El TTL pasa a ser el árbitro de la corrección**, y eso es inseguro por la razón de
  §2.5: la expiración de la clave no cancela el POST en vuelo. En PostgreSQL el árbitro es
  la `EXCLUDE` constraint y el TTL solo sirve para recuperación de crash — papel mucho
  menor. Esta es la diferencia decisiva entre ambas opciones, no una preferencia de stack.
- No detecta solapamientos parciales (la clave es un instante, no un rango). Habría que
  bloquear todos los sub-intervalos de la rejilla, lo cual es frágil.
- Redis en modo no-cluster es SPOF; en cluster, `SET NX` no es seguro sin Redlock, que tiene
  objeciones conocidas.
- No deja auditoría.

**Recomendación: usar PostgreSQL.** Redis sólo si la plataforma lo impone. Si se impone,
añadir *fencing token* monótono y validarlo en el momento de escribir, o el problema del
TTL queda sin resolver.

### 6.2 Hold de negocio visible al cliente

Reserva **de negocio**, no lock técnico. Patrón Ticketmaster / asiento de avión: el usuario
ve "tienes 10 minutos para completar la reserva".

Solo aporta valor si el flujo es multi-paso:

```
elegir slot → [rellenar datos / pagar] → confirmar
                     ↑ aquí el slot debe quedar retenido
```

**Con la API actual no aporta nada.** `POST /api/businesses/{id}/appointments` recibe todo
el payload de una vez (`CreateAppointmentRequest` lleva ya slot + cliente + servicio). En un
flujo de un solo disparo el lock técnico de la Fase 2 dura milisegundos y cubre el caso
entero. Un hold de 10 minutos sobre eso sería inventario congelado sin contrapartida.

Coste si se decide implementarlo:

- Recurso nuevo completo: `POST /holds`, `DELETE /holds/{id}`, y confirmación que acepta
  `holdId`. Se reutiliza `slot_reservation` con un estado `HELD` y `expires_at` más largo.
- Expiración visible al usuario → contrato de API y mensajes de error nuevos.
- Decisión de producto: qué ocurre al expirar (¿se avisa? ¿se puede extender?).
- **Rate-limit obligatorio, no opcional.** Sin él, cualquiera agota el inventario pidiendo
  holds en bucle sin confirmar nunca. Límite por IP y por identificador de cliente.

**Recomendación: aplazar** hasta responder la pregunta 6 de §10. Si el flujo resulta ser
multi-paso, el hold sube a necesario y debe diseñarse **junto** con la Fase 2 (mismo
esquema, mismo estado), no como añadido posterior.

---

## 7. Orden de ejecución y estimación

| Fase | Descripción | Prioridad | Estimación |
|------|-------------|-----------|-----------|
| 0 | Test concurrente que reproduce el fallo | Bloqueante | 0,5 d |
| 6 | Thread-safety de `GraphAuthService` | Alta | 0,5 d |
| 1 | Idempotencia | Alta | 1,5 d |
| 2 | Reserva atómica en BD (incluye job de liberación **verificada**) | **Crítica** | 3 d |
| 7 | Bajar `responseTimeout` a 10 s — **solo después de la Fase 1** | Alta | 0,25 d |
| 3 | Precomprobación de disponibilidad | Media | 1 d |
| 4 | Job de reconciliación | Media | 2 d |
| 5 | Métricas y alertas | Media | 1 d |
| — | Hold de negocio visible (§6.2) | Aplazada | — |

**Mínimo desplegable a producción: fases 0 + 1 + 2 + 6 + 7.** ≈ 5,75 días.
Las fases 3–5 son mejoras posteriores y pueden ir en un segundo PR.

Orden obligatorio: **1 antes que 7.** Reducir el timeout aumenta los reintentos; sin
idempotencia eso multiplica los duplicados en vez de reducirlos.

---

## 8. Cambios estructurales que implica

Nuevos artefactos:

```
domain/port/out/SlotReservierung.java          (puerto de salida — nuevo)
domain/model/SlotReservation.java              (entidad de dominio)
infrastructure/persistence/SlotReservationJpaAdapter.java
infrastructure/persistence/SlotReservationEntity.java
infrastructure/persistence/SlotReservationRepository.java
exception/SlotConflictException.java
config/SchedulingConfig.java                   (para @Scheduled)
db/migration/V1__slot_reservation.sql          (Flyway)
```

Modificados:

```
service/AppointmentService.java                (orquesta reserva → Graph → confirmación)
controller/AppointmentController.java          (cabecera Idempotency-Key)
exception/GlobalExceptionHandler.java          (mapear SlotConflictException → 409)
pom.xml                                        (spring-boot-starter-data-jpa, postgresql, flyway)
application.yml                                (datasource, TTLs, cron de reconciliación)
```

La arquitectura Onion se respeta: `AppointmentService` habla con un **puerto**
`SlotReservierung`; JPA queda confinado a infraestructura, igual que `GraphApiClient` hoy.

---

## 9. Criterios de aceptación

- [ ] El test de la Fase 0 pasa: 20 peticiones concurrentes idénticas → 1 cita, 19 × `409`.
- [ ] 20 peticiones con la misma `Idempotency-Key` → 1 cita, 19 respuestas idénticas `200`.
- [ ] Test de solapamiento parcial: 10:00–11:00 y 10:30–11:30 mismo empleado → la 2ª da `409`.
- [ ] Test de zona horaria: `10:00 Europe/Berlin` y `08:00 UTC` se detectan como el mismo slot.
- [ ] Fallo simulado de Graph tras adquirir la reserva → fila queda `RELEASED`, slot reutilizable.
- [ ] Instancia matada entre reserva y POST **sin que la cita llegara a crearse** → job
      pasa la fila a `RELEASED` en < 5 min y el slot vuelve a estar disponible.
- [ ] Instancia matada entre reserva y POST **con la cita ya creada en Graph** → job la
      detecta vía `/calendarView`, pasa la fila a `CONFIRMED` con su `graph_appointment_id`
      y **no** libera el slot. (Test de la liberación verificada, §2.5.)
- [ ] Graph responde más lento que el TTL de `PENDING` → no se produce doble reserva.
- [ ] Cancelar una cita libera el slot y permite volver a reservarlo.
- [ ] Reprogramar libera el slot antiguo y ocupa el nuevo atómicamente.
- [ ] Prueba de carga con 2 réplicas tras un balanceador: cero solapamientos en 1000 peticiones.

---

## 10. Preguntas abiertas para el negocio

1. ¿Algún servicio admite más de un asistente por slot (`maximumAttendeesCount > 1`)?
   Cambia el diseño de la Fase 2 (§2.2).
2. ¿Se permite deliberadamente el over-booking administrativo? Si sí, hace falta un flag de
   bypass para llamadores con rol admin.
3. ¿Hay citas creadas fuera de esta API (portal de auto-reserva, admin de Bookings)? Si sí,
   la Fase 4 sube a prioridad alta.
4. ¿Cuántos `bookingBusinesses` hay que reconciliar y con qué frecuencia? Determina el coste
   en cuota de Graph del job de la Fase 4.
5. Ante una colisión detectada a posteriori: ¿cancelar automáticamente o escalar a un humano?
6. **¿El frontend envía un único POST con todo el payload, o el usuario elige slot y luego
   rellena datos / paga en una pantalla aparte?** Decide §6.2. Si es multi-paso, el hold de
   negocio deja de ser aplazable y hay que diseñarlo junto con la Fase 2.
