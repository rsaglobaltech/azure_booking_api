# Azure Buchungs-API — Spring Boot + Microsoft Graph

REST-API in Spring Boot 3, die als Proxy zur **Microsoft Bookings API (Graph v1.0)** fungiert.
Ermöglicht die Verwaltung von Buchungsagenturen, Mitarbeitern, Diensten und Terminen
innerhalb eines einzigen Azure-AD-Mandanten.

> **Architektur:** Onion Architecture (Ports & Adapters)
> **Dokumentation:** [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md)

---

## Buchungs-URL (dynamisch)

Jede Agentur hat eine eindeutige, dynamische öffentliche Buchungsseite:

```
https://outlook.office.com/book/{agenturName}@midominio.com
```

Der `{agenturName}` wird immer dynamisch aus der Agentur-ID abgeleitet – niemals statisch.
Die berechnete URL wird als `buchungsUrl` in jeder API-Antwort zurückgegeben.

---

## Voraussetzungen

### 1. Anwendung in Azure AD registrieren

1. **Azure Portal** → Azure Active Directory → App-Registrierungen → Neue Registrierung
2. Name: `AzureBuchungsAPI`
3. Kontotyp: *Nur diese Organisation (einzelner Mandant)*
4. **Client-Secret** erstellen unter *Zertifikate und Geheimnisse*
5. **Anwendungsberechtigungen** hinzufügen unter *API-Berechtigungen → Microsoft Graph*:

   | Berechtigung | Typ |
   |-------------|-----|
   | `Bookings.ReadWrite.All` | Application |
   | `BookingsAppointment.ReadWrite.All` | Application |

6. **Administratorzustimmung erteilen** für beide Berechtigungen

### 2. Umgebungsvariablen setzen

```powershell
# Windows PowerShell
$env:AZURE_TENANT_ID     = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
$env:AZURE_CLIENT_ID     = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
$env:AZURE_CLIENT_SECRET = "IhrClientSecret"
```

Oder direkt in `src/main/resources/application.yml` eintragen.

---

## Anwendung starten

```bash
./mvnw spring-boot:run
```

Die API ist unter `http://localhost:8080` erreichbar.

---

## Verfügbare Endpunkte

### Agenturen (Buchungsbetriebe)

| Methode | URL | Beschreibung |
|--------|-----|--------------|
| `GET` | `/api/businesses` | Alle Agenturen des Mandanten (inkl. `buchungsUrl`) |
| `GET` | `/api/businesses/{businessId}` | Agentur abrufen (inkl. dynamischer `buchungsUrl`) |
| `POST` | `/api/businesses` | Neue Agentur erstellen |
| `PUT` | `/api/businesses/{businessId}` | Agentur aktualisieren |
| `DELETE` | `/api/businesses/{businessId}` | Agentur löschen |
| `POST` | `/api/businesses/{businessId}/publish` | Buchungsseite veröffentlichen |
| `POST` | `/api/businesses/{businessId}/unpublish` | Buchungsseite deaktivieren |

### Mitarbeiter

| Methode | URL | Beschreibung |
|--------|-----|--------------|
| `GET` | `/api/businesses/{businessId}/staff` | Alle Mitarbeiter einer Agentur |
| `GET` | `/api/businesses/{businessId}/staff/{staffId}` | Mitarbeiter abrufen |
| `POST` | `/api/businesses/{businessId}/staff` | Mitarbeiter anlegen |
| `PUT` | `/api/businesses/{businessId}/staff/{staffId}` | Mitarbeiter aktualisieren |
| `DELETE` | `/api/businesses/{businessId}/staff/{staffId}` | Mitarbeiter entfernen |
| **`POST`** | **`/api/businesses/{businessId}/staff/availability`** | **Verfügbarkeit abfragen (frei/belegt)** |

### Termine (Appointments)

| Methode | URL | Beschreibung |
|--------|-----|--------------|
| `GET` | `/api/businesses/{businessId}/appointments` | Alle Termine auflisten |
| `GET` | `/api/businesses/{businessId}/appointments/calendar?startDateTime=...&endDateTime=...` | Kalenderansicht (Zeitraum) |
| `GET` | `/api/businesses/{businessId}/appointments/{appointmentId}` | Termin abrufen |
| `POST` | `/api/businesses/{businessId}/appointments` | Termin erstellen |
| `PUT` | `/api/businesses/{businessId}/appointments/{appointmentId}` | Termin aktualisieren |
| `DELETE` | `/api/businesses/{businessId}/appointments/{appointmentId}` | Termin stornieren |

### Dienste

| Methode | URL | Beschreibung |
|--------|-----|--------------|
| `GET` | `/api/businesses/{businessId}/services` | Alle Dienste auflisten |
| `GET` | `/api/businesses/{businessId}/services/{serviceId}` | Dienst abrufen |
| `POST` | `/api/businesses/{businessId}/services` | Dienst erstellen |
| `PUT` | `/api/businesses/{businessId}/services/{serviceId}` | Dienst aktualisieren |
| `DELETE` | `/api/businesses/{businessId}/services/{serviceId}` | Dienst löschen |

---

## Verwendungsbeispiele

### Alle Agenturen des Mandanten abrufen

```bash
GET http://localhost:8080/api/businesses
```

**Antwort-Beispiel (Feld `buchungsUrl` wird dynamisch berechnet):**
```json
[
  {
    "id": "agenturfreiburg@midominio.com",
    "displayName": "Agentur Freiburg",
    "isPublished": true,
    "buchungsUrl": "https://outlook.office.com/book/agenturfreiburg@midominio.com"
  }
]
```

### Mitarbeiterverfügbarkeit abfragen (frei / belegt)

```bash
POST http://localhost:8080/api/businesses/{businessId}/staff/availability
Content-Type: application/json

{
  "staffIds": ["311a5454-08b2-4560-ba1c-f715e938cb79"],
  "startDateTime": { "dateTime": "2024-06-15T08:00:00", "timeZone": "Europe/Berlin" },
  "endDateTime":   { "dateTime": "2024-06-15T18:00:00", "timeZone": "Europe/Berlin" }
}
```

**Verfügbarkeitsstatus im Feld `status`:**
- `Available`      → Mitarbeiter ist **frei**
- `Busy`           → Mitarbeiter hat einen **Termin** (Dienst-ID in `serviceId`)
- `SlotsAvailable` → Freie Zeitfenster vorhanden
- `OutOfOffice`    → Mitarbeiter ist außer Haus

```json
[
  {
    "staffId": "311a5454-08b2-4560-ba1c-f715e938cb79",
    "availabilityItems": [
      {
        "status": "Available",
        "startDateTime": { "dateTime": "2024-06-15T08:00:00", "timeZone": "Europe/Berlin" },
        "endDateTime":   { "dateTime": "2024-06-15T10:00:00", "timeZone": "Europe/Berlin" },
        "serviceId": ""
      },
      {
        "status": "Busy",
        "startDateTime": { "dateTime": "2024-06-15T10:00:00", "timeZone": "Europe/Berlin" },
        "endDateTime":   { "dateTime": "2024-06-15T11:00:00", "timeZone": "Europe/Berlin" },
        "serviceId": "57da6774-a087-4d69-b0e6-6fb82c339976"
      }
    ]
  }
]
```

### Termin erstellen

```bash
POST http://localhost:8080/api/businesses/{businessId}/appointments
Content-Type: application/json

{
  "serviceId": "57da6774-a087-4d69-b0e6-6fb82c339976",
  "startDateTime": { "dateTime": "2024-06-15T10:00:00", "timeZone": "Europe/Berlin" },
  "endDateTime":   { "dateTime": "2024-06-15T11:00:00", "timeZone": "Europe/Berlin" },
  "staffMemberIds": ["311a5454-08b2-4560-ba1c-f715e938cb79"],
  "customers": [
    { "name": "Max Mustermann", "emailAddress": "max@beispiel.de", "phone": "+49151000000" }
  ],
  "isLocationOnline": false,
  "serviceNotes": "Erstgespräch"
}
```

### Mitarbeiter anlegen

```bash
POST http://localhost:8080/api/businesses/{businessId}/staff
Content-Type: application/json

{
  "displayName": "Anna Müller",
  "emailAddress": "anna.mueller@midominio.com",
  "role": "teamMember",
  "timeZone": "Europe/Berlin",
  "useBusinessHours": true,
  "availabilityIsAffectedByPersonalCalendar": true,
  "isEmailNotificationEnabled": true
}
```

---

## Onion-Architektur – Projektstruktur

```
src/main/java/com/booking/azure/
│
├── domain/port/in/              ← Eingehende Ports (Use-Case-Interfaces)
│   ├── TerminVerwaltung
│   ├── AgenturVerwaltung
│   ├── DienstVerwaltung
│   └── MitarbeiterVerwaltung
│
├── domain/port/out/             ← Ausgehender Port (Adapter-Interface)
│   └── GraphApiAnfrage
│
├── service/                     ← Anwendungsschicht (implementiert Ports)
│   ├── AppointmentService       → implements TerminVerwaltung
│   ├── BookingBusinessService   → implements AgenturVerwaltung (+ buchungsUrl)
│   ├── BookingServiceMgr        → implements DienstVerwaltung
│   ├── StaffMemberService       → implements MitarbeiterVerwaltung
│   ├── GraphApiClient           → implements GraphApiAnfrage
│   └── GraphAuthService         (OAuth2 Client Credentials / MSAL4J)
│
├── controller/                  ← Präsentationsschicht (nutzt Ports)
│   ├── AppointmentController
│   ├── BookingBusinessController
│   ├── BookingServiceController
│   └── StaffMemberController
│
├── config/                      ← Infrastrukturkonfiguration
│   ├── CorsConfig
│   ├── GraphApiProperties
│   └── WebClientConfig
│
├── dto/                         ← Datentransfer-Objekte
│   └── request/
│
└── exception/
    └── GlobalExceptionHandler
```

---

## Benötigte Azure-AD-Berechtigungen

```
Bookings.ReadWrite.All            (Application) — Agenturen, Mitarbeiter, Dienste
BookingsAppointment.ReadWrite.All (Application) — Termine erstellen und lesen
```

Authentifizierungsfluss: **Client Credentials** (ohne Benutzer, ohne Delegation),
geeignet für Backend-zu-Backend-Integrationen innerhalb desselben Mandanten.

---

> Vollständige Architektur-Dokumentation und Diagramme: [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md)
