# ADR-0004: Kompensation unterscheidet Ablehnung von Schweigen

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `AppointmentService#createAppointment`, `GlobalExceptionHandler`

## Kontext

Nach der Reservierung folgt der Aufruf an Microsoft Graph. Er kann auf drei
Arten enden:

1. **Erfolg** — der Termin existiert.
2. **Ablehnung** (`4xx`/`5xx` mit Antwort) — der Termin existiert **nicht**.
3. **Schweigen** (Zeitüberschreitung, Verbindungsabbruch) — **unbekannt**.

Fall 3 ist der gefährliche. Eine Zeitüberschreitung im Client bricht die
Verarbeitung auf der Gegenseite nicht ab: der `POST` kann Graph sehr wohl
erreichen und einen Termin anlegen, nachdem wir aufgegeben haben.

Wer 2 und 3 gleich behandelt und in beiden Fällen freigibt, baut sich diesen
Ablauf:

```
t=0    A reserviert, POST abgesetzt
t=0,1  Graph ist überlastet und braucht 120 s
t=30   A läuft in die Zeitüberschreitung und gibt frei. Slot frei.
t=31   B belegt den Slot, POST → 201
t=120  A's POST erreicht Graph doch noch → 201
       Zwei überlappende Termine. Lautlos.
```

## Entscheidung

Die beiden Fälle werden getrennt behandelt:

| Ausgang | Reservierung | HTTP-Antwort |
|---------|--------------|--------------|
| Erfolg | `CONFIRMED` | 201 / 200 |
| Ablehnung (`GraphResponseException`) | `RELEASED` | 502 Bad Gateway |
| Schweigen (jede andere `RuntimeException`) | **bleibt `PENDING`** | 504 Gateway Timeout |

Bei Schweigen wird **nichts freigegeben**. Der Slot bleibt belegt, bis der
Wiederherstellungsjob bei Graph nachgesehen hat.

Auch die Statuscodes unterscheiden sich, weil der Unterschied für den Aufrufer
handlungsrelevant ist: bei `502` steht fest, dass nichts angelegt wurde; bei
`504` kann der Termin existieren.

## Konsequenzen

### Positiv

- Der lautlose Doppelbuchungs-Ablauf oben ist ausgeschlossen.
- Der Aufrufer erfährt den Unterschied zwischen „nicht passiert" und „unklar"
  und kann danach handeln.

### Negativ

- Ein Slot kann nach einer Zeitüberschreitung belegt bleiben, obwohl gar kein
  Termin entstand — bis der Wiederherstellungslauf ihn prüft. **Ein zeitweise zu
  Unrecht belegter Slot ist der billigere Fehler** als eine Doppelbuchung.
- Ein blinder Wiederholungsversuch des Aufrufers ist nicht sicher, solange
  Idempotenz fehlt. Derzeit erhält er `409`, weil der Slot noch belegt ist —
  was den zweiten Termin verhindert, aber aus dem richtigen Grund nur zufällig.

## Verworfene Alternativen

**Immer freigeben.** Der einfache Weg, und genau der Ablauf oben.

**Nie freigeben.** Sicher gegen Doppelbuchungen, aber jede abgelehnte Anfrage
blockiert den Slot bis zum Ablauf der Frist. Bei Ablehnung ist sicher bekannt,
dass nichts entstand — diese Information wegzuwerfen wäre Verschwendung.

**Idempotenzschlüssel je Buchungsversuch.** Die eigentlich richtige Lösung: ein
Wiederholungsversuch würde denselben Termin treffen statt einen zweiten
anzulegen. Setzt Unterstützung auf Graph-Seite voraus, die es für
`bookingAppointment` nicht gibt. Bleibt offen.
