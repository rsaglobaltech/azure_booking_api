# ADR-0005: Wiederherstellung fragt zuerst bei Graph nach

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `SlotRecoveryService`
- **Folgt aus:** [ADR-0004](0004-kompensation-unterscheidet-ablehnung-von-schweigen.md)

## Kontext

Weil bei ausbleibender Antwort nichts freigegeben wird, bleibt nach jeder
Zeitüberschreitung eine Reservierung auf `PENDING` stehen. Das ist beabsichtigt
— aber ohne Gegenmaßnahme bliebe der Slot **dauerhaft** blockiert.

Jede Reservierung trägt ein `expires_at`. Die naheliegende Lösung wäre ein Job,
der abgelaufene Zeilen freigibt.

Diese naheliegende Lösung erzeugt genau den Fehler, den sie beheben soll:

```
t=0    A reserviert, expires_at = t+90. POST an Graph abgesetzt.
t=0,1  Graph ist überlastet und braucht 120 s.
t=90   Ein blinder Job gibt die "abgelaufene" Zeile frei. Slot frei.
t=91   B belegt den Slot, POST → 201
t=120  A's POST erreicht Graph doch noch → 201
       Zwei überlappende Termine. Lautlos.
```

**Das Ablaufen einer Frist bricht keinen laufenden HTTP-Aufruf ab.** `expires_at`
beweist nicht, dass der Termin fehlt.

## Entscheidung

`expires_at` markiert eine Zeile ausschließlich als *prüfenswert*, nie als
*freizugeben*. Der Wiederherstellungsjob fragt für jede verwaiste Reservierung
`GET /calendarView` ab und entscheidet erst danach:

| Befund | Entscheidung |
|--------|--------------|
| Passender Termin existiert | `CONFIRMED` — **wiederherstellen, nicht freigeben.** Der Schreibvorgang hat stattgefunden. |
| Kein passender Termin | `RELEASED` — der Slot wird wieder buchbar. |
| Graph nicht erreichbar | **nichts tun.** Nächster Lauf erneut. |

Weitere Festlegungen:

- **Abgleich über Überlappung, nicht über Gleichheit.** Bookings kann Vor- und
  Nachbereitungszeiten aufschlagen, und Graph liefert Zeiten je nach Endpunkt in
  unterschiedlichen Zonen. Ein zu strenger Vergleich verfehlte den Termin und
  gäbe den Slot fälschlich frei — der teurere Fehler.
- **Eine Kalenderabfrage je Agentur**, nicht je Zeile. Ein Termin mit mehreren
  Mitarbeitern erzeugt mehrere Zeilen im selben Zeitraum.
- **Entschieden wird über das ganze Aggregat.** Zu einer verwaisten Zeile wird
  das zugehörige `Booking` geladen; die Entscheidung gilt für alle seine Slots.
  Sonst könnte eine Buchung mit zwei Mitarbeitern halb bestätigt enden.

## Konsequenzen

### Positiv

- Hängengebliebene Reservierungen lösen sich auf, ohne je einen Slot
  freizugeben, den ein bestehender Termin belegt.
- Nicht erreichbares Graph führt zu Stillstand, nicht zu falschen
  Entscheidungen.

### Negativ

- Der Lauf kostet Graph-Aufrufe.
- Ein Slot bleibt bis zum nächsten Lauf belegt (Standard: alle fünf Minuten).
- Der Abgleich über Überlappung kann theoretisch einen fremden Termin desselben
  Mitarbeiters im selben Zeitraum treffen. In dem Fall wird eine Reservierung
  bestätigt, die zu einem anderen Termin gehört — der Slot bleibt belegt, was
  die sichere Richtung ist.

## Verworfene Alternativen

**Abgelaufene Zeilen blind freigeben.** Der Ablauf oben.

**Abgelaufene Zeilen nie anfassen.** Slots blieben für immer blockiert.

**Kürzere Frist.** Verschiebt nur, wie schnell der falsche Schluss gezogen wird.
Das Problem ist nicht die Länge der Frist, sondern die Annahme, dass ihr Ablauf
etwas über den Termin aussagt.
