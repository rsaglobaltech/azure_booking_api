# ADR-0008: Kundendaten werden nicht lokal gespeichert

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `Booking#customer`, `BookingConfirmed`, `SendBookingConfirmationHandler`

## Kontext

Für die Bestätigungsmail werden Name und E-Mail-Adresse des Kunden gebraucht.
Diese Angaben kommen mit der Anfrage herein und gehen an Microsoft Bookings, wo
der Kundendatensatz geführt wird.

Die Frage war, ob dieses System sie zusätzlich in `slot_reservation` ablegt.

## Entscheidung

**Nein.** `Booking.customer` ist ein Feld im Speicher, das nicht persistiert
wird. Die Tabelle enthält keine personenbezogenen Daten — nur Kennungen,
Zeitfenster und Zustand.

Daraus folgt: eine aus der Datenbank wiederhergestellte Buchung hat **keinen
Kunden**. `BookingConfirmed` trägt den Kunden deshalb als `Optional`, und
`SendBookingConfirmationHandler` versendet nichts, wenn er fehlt.

Das ist kein Verlust, sondern das gewünschte Verhalten: die einzige Buchung ohne
Kunden ist eine, die der Wiederherstellungsjob bestätigt hat — und deren
Bestätigungsmail beim ursprünglichen Anlegen bereits verschickt wurde. Eine
zweite wäre falsch.

### Nebenbefund

Der bisherige Code ersetzte fehlenden Namen und fehlende Adresse durch die
Zeichenkette `"Unknown"`:

```java
.customerName(request.getCustomers() != null && !request.getCustomers().isEmpty()
        ? request.getCustomers().get(0).getName() : "Unknown")
```

Damit entstanden Bestätigungsmails an niemanden unter der ungültigen Adresse
`Unknown`. Ersatzlos entfernt: **fehlend ist fehlend.** `CustomerContact` weist
leere Werte und Adressen ohne `@` zurück.

## Konsequenzen

### Positiv

- Keine personenbezogenen Daten in dieser Datenbank. Kein Auskunfts-, Lösch- oder
  Aufbewahrungsproblem, keine zweite Kopie, die veralten kann.
- Microsoft Bookings bleibt die einzige Quelle der Wahrheit für Kundendaten.
- Der fehlgeleitete E-Mail-Versand an `Unknown` ist verschwunden.

### Negativ

- Eine wiederhergestellte Buchung kann keine Benachrichtigung auslösen. Für den
  heutigen Zweck richtig; wäre eine Benachrichtigung nach Wiederherstellung
  einmal erwünscht, müssten die Angaben aus Graph nachgeladen werden.
- Der Kunde steht nur im Speicher — wer `Booking` erweitert, muss wissen, dass
  dieses Feld eine Umbuchung durch die Datenbank nicht überlebt. Im Code
  vermerkt.

## Verworfene Alternativen

**Kunden mitspeichern.** Macht Benachrichtigungen nach Wiederherstellung
möglich, zum Preis einer zweiten Kopie personenbezogener Daten in einem System,
das sie nicht besitzt und nicht pflegt.

**Kunden aus Graph nachladen, wenn er fehlt.** Vermeidet die Kopie, kostet aber
einen Fremdsystemaufruf in einem Hintergrundjob und ändert nichts am
Wesentlichen: die E-Mail wurde bereits verschickt.
