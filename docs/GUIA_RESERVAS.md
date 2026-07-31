# Guía Paso a Paso: Creación de Reservas (Bookings) a través de Microsoft Graph API

Esta guía detalla el proceso técnico para crear citas y reservas de forma programática utilizando la **API de Microsoft Graph**, en la cual se apoya internamente Microsoft Bookings.

> **Importante (Concepto Clave):**
> La URL pública de cada agencia (ej. `https://outlook.office.com/book/{agencia}@midominio.com`) es un **frontend exclusivo para clientes**. No expone una API REST pública. Toda automatización e integración backend debe realizarse a través de **Microsoft Graph API**, comunicándose con el "BookingBusiness" correspondiente dentro de tu tenant.

---

## Prerrequisitos de Autenticación

Dado que la integración es de tipo backend-a-backend (sin intervención directa de un usuario de Office 365 en el proceso de login), se utiliza el flujo de **Client Credentials** de OAuth2.

1. **Registrar la App en Azure AD**: Obtener el `Tenant ID`, `Client ID` y generar un `Client Secret`.
2. **Permisos de API (Microsoft Graph)**:
   - `Bookings.ReadWrite.All` (Tipo Application): Para gestionar agencias, servicios y staff.
   - `BookingsAppointment.ReadWrite.All` (Tipo Application): Para leer y escribir citas.
3. **Consentimiento de Administrador**: Los permisos deben estar aprobados a nivel de tenant.
4. **Obtener el Token**: Realizar una petición a Azure AD para obtener el `Bearer Token` que se enviará en el header `Authorization` de las peticiones a Graph.

---

## Flujo de Reserva Paso a Paso

Para realizar una reserva exitosa, necesitas recopilar información secuencialmente (Agencia -> Servicio -> Staff -> Disponibilidad -> Reserva). Si ya conoces los IDs de antemano (por ejemplo, están cacheados en tu base de datos), puedes saltar directamente al Paso 4 y 5.

### Paso 1: Identificar la Agencia (Booking Business)

Cada página de Bookings es un negocio (`bookingBusiness`) independiente dentro de tu tenant de Office 365. Debemos identificar su `id` (que habitualmente coincide con el correo asociado a la agencia).

- **Petición Graph API:** `GET /v1.0/solutions/bookingBusinesses`
- **Petición al Proxy Spring Boot:** `GET /api/businesses`

**Respuesta esperada:**
Identificarás la agencia objetivo y anotarás su `id` (ej. `agenturfreiburg@midominio.com`).

### Paso 2: Seleccionar el Servicio a Reservar

Una agencia puede ofrecer múltiples servicios (ej. Consultoría inicial, Soporte técnico, etc.). Cada servicio tiene duración y reglas específicas.

- **Petición Graph API:** `GET /v1.0/solutions/bookingBusinesses/{businessId}/services`
- **Petición al Proxy Spring Boot:** `GET /api/businesses/{businessId}/services`

**Respuesta esperada:**
Anotarás el `id` del servicio que el cliente desea contratar (ej. `57da6774-a087-4d69-b0e6-6fb82c339976`).

### Paso 3: Identificar al Staff (Agentes)

Para asignar la reserva, normalmente requieres especificar qué miembro del equipo atenderá al cliente.

- **Petición Graph API:** `GET /v1.0/solutions/bookingBusinesses/{businessId}/staff`
- **Petición al Proxy Spring Boot:** `GET /api/businesses/{businessId}/staff`

**Respuesta esperada:**
Anotarás el `id` del miembro del equipo (ej. `311a5454-08b2-4560-ba1c-f715e938cb79`).

### Paso 4: Consultar la Disponibilidad (Opcional pero recomendado)

Antes de intentar programar la cita en un hueco determinado, es buena práctica confirmar si el agente está libre en ese horario.

- **Petición Graph API:** `POST /v1.0/solutions/bookingBusinesses/{businessId}/getStaffAvailability`
- **Petición al Proxy Spring Boot:** `POST /api/businesses/{businessId}/staff/availability`

**Cuerpo de la Petición (Payload):**
```json
{
  "staffIds": ["311a5454-08b2-4560-ba1c-f715e938cb79"],
  "startDateTime": { "dateTime": "2024-06-15T08:00:00", "timeZone": "Europe/Berlin" },
  "endDateTime":   { "dateTime": "2024-06-15T18:00:00", "timeZone": "Europe/Berlin" }
}
```

La respuesta indicará el estado (`status`) de cada bloque de tiempo (`Available`, `Busy`, `OutOfOffice`). 

### Paso 5: Crear la Cita (Appointment)

Con todos los IDs necesarios y la seguridad de que hay disponibilidad, procedemos a crear la reserva final. Esto agregará la cita al calendario de la agencia y enviará las invitaciones / correos pertinentes si así está configurado.

- **Petición Graph API:** `POST /v1.0/solutions/bookingBusinesses/{businessId}/appointments`
- **Petición al Proxy Spring Boot:** `POST /api/businesses/{businessId}/appointments`

**Cuerpo de la Petición (Payload):**
```json
{
  "serviceId": "57da6774-a087-4d69-b0e6-6fb82c339976",
  "startDateTime": { "dateTime": "2024-06-15T10:00:00", "timeZone": "Europe/Berlin" },
  "endDateTime":   { "dateTime": "2024-06-15T11:00:00", "timeZone": "Europe/Berlin" },
  "staffMemberIds": [
    "311a5454-08b2-4560-ba1c-f715e938cb79"
  ],
  "customers": [
    { 
      "name": "Max Mustermann", 
      "emailAddress": "max@beispiel.de", 
      "phone": "+49151000000" 
    }
  ],
  "isLocationOnline": false,
  "serviceNotes": "Primera consulta de asesoramiento (Notas internas)."
}
```

**Respuesta esperada:**
HTTP 201 Created. La respuesta devolverá el objeto `BookingAppointment` generado, incluyendo su propio `id` que te servirá en caso de que necesites cancelarlo (DELETE) o modificarlo (PUT) en un futuro.

---

## Resumen Arquitectónico de este Proyecto

Este proyecto actúa como un intermediario (Proxy Backend) entre tu aplicación y Microsoft Graph.

1. Tu frontend/backend llama a los endpoints de esta API (ej. `http://localhost:8080/api/businesses/...`).
2. El servicio de autenticación interno (`GraphAuthService`) se encarga de negociar y cachear el token de Azure AD.
3. El cliente REST interno (`GraphApiClient`) realiza la llamada real hacia `graph.microsoft.com`.
4. El resultado se transforma y se sirve de vuelta a tu aplicación.

Esto abstrae toda la complejidad de los tokens OAuth2 y el manejo estricto de Microsoft Graph, exponiendo una API más sencilla de consumir para el resto de tus herramientas internas.
