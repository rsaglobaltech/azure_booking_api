Tengo Microsoft Bookings “hosteado” en un tenant único, es decir:

Todos los agentes de las distintas agencias usan el mismo tenant de Azure.
Cada agencia tiene su página de Bookings, algo como:
https://outlook.office.com/book/{name_agentur}@midominio.com

🧠 Clave: Microsoft Bookings vs Graph Calendar
Microsoft Bookings es un producto de front-end para clientes: reserva de citas vía web.
Internamente, Bookings se apoya en Microsoft Graph:
Cada “calendar” de Bookings se traduce a un calendario de Exchange Online en tu tenant.
No hay un API REST público de “Bookings web” que consuma directamente outlook.office.com/book/....
🔹 Lo que sí puedes hacer

Microsoft proporciona REST API vía Graph:

GET /solutions/bookingBusinesses → lista negocios (cada página de Bookings).
GET /solutions/bookingBusinesses/{id}/appointments → lista citas.
POST /solutions/bookingBusinesses/{id}/appointments → crear cita programáticamente.

💡 Esto no usa la URL de Bookings directamente, sino el tenant y la API de Graph.

Documentación oficial:
Microsoft Graph – Bookings API

🔹 Flujo recomendado para tu caso
Registrar la aplicación en Azure AD (Tenant único).
Dar permisos a Graph API:
Bookings.ReadWrite.All
Obtener tenantId + clientId + clientSecret → token OAuth2.
Llamadas REST vía Graph:
GET /solutions/bookingBusinesses
GET /solutions/bookingBusinesses/{businessId}/appointments
POST /solutions/bookingBusinesses/{businessId}/appointments

✅ Todo dentro del mismo tenant, sin usar la URL pública /book/....

🔹 Por qué no usar la URL directa
https://outlook.office.com/book/{name_agentur}@...
Esa URL es para clientes y navegador
No tiene API REST pública
Solo Graph te permite automatizar reservas
🔹 Resumen
Concepto	Uso correcto
Bookings URL web	Solo front-end para clientes
Automatización / REST API	Microsoft Graph → /solutions/bookingBusinesses/{id}/appointments
Tenant	Siempre el mismo (tu caso)
Multi-agencia	Cada agencia = “BookingBusiness” dentro del tenant

