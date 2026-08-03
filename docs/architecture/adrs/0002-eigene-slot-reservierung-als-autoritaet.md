# ADR-0002: Eigene Slot-Reservierung als Autorität für den gegenseitigen Ausschluss

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `BookingRepository`, `BookingJpaAdapter`, Tabelle `slot_reservation`

## Kontext

Zwei Kunden dürfen denselben Mitarbeiter nicht zur selben Zeit buchen. Die
naheliegende Annahme wäre, dass Microsoft Bookings das verhindert. Es tut es
nicht:

- Die Graph-API bietet auf `bookingAppointment` **keine Sperren**, **keine
  Transaktionen** und **keine bedingten Schreibvorgänge**. Es gibt kein
  „lege an, falls frei".
- Der verwendete Endpunkt ist der administrative. Er erlaubt Überbuchung
  **bewusst** — Personal soll Termine auch dann eintragen können, wenn der
  Kalender formal voll ist.

Zwei gleichzeitige Anfragen erhalten also beide `201 Created`, und niemand
erfährt davon.

## Entscheidung

Das System führt eine **eigene Reservierungstabelle** (`slot_reservation`) und
entscheidet dort über den gegenseitigen Ausschluss, bevor Graph überhaupt
gefragt wird.

Feste Aufrufreihenfolge:

```
1. reserve(...)              eigene Transaktion, sofort festgeschrieben
2. POST an Microsoft Graph   außerhalb jeder Transaktion
3a. confirm(...)             bei Erfolg
3b. release(...)             bei Ablehnung (Kompensation)
```

**Schritt 2 darf nicht in einer offenen Transaktion laufen.** Eine Transaktion
über einen Netzaufruf hinweg offen zu halten erschöpft unter Last den
Verbindungspool — aus einem Korrektheitsproblem würde ein Ausfall.

Blockierend sind die Zustände `PENDING` und `CONFIRMED`; nur `RELEASED` gibt den
Slot wieder frei. Die freigegebene Zeile bleibt als Prüfspur erhalten.

## Konsequenzen

### Positiv

- Der Ausschluss liegt dort, wo Transaktionen und Sperren tatsächlich
  existieren: in der eigenen Datenbank.
- Die Entscheidung fällt, **bevor** ein Termin in Graph entsteht. Ein Konflikt
  kostet keinen Fremdsystemaufruf.
- Der Zustand ist einsehbar und nachvollziehbar, statt in einem fremden System
  zu liegen.

### Negativ

- Zwei Wahrheiten, die auseinanderlaufen können. Termine, die außerhalb dieses
  Systems in Bookings angelegt werden, kennt die Tabelle nicht und blockieren
  hier nichts.
- Jede Buchung schreibt zusätzlich lokal.
- Es braucht einen Wiederherstellungsjob für Reservierungen, die zwischen
  Schritt 1 und 3 hängenbleiben — siehe [ADR-0005](0005-wiederherstellung-fragt-zuerst-bei-graph-nach.md).

## Verworfene Alternativen

**Auf Graph vertrauen.** Am einfachsten und schlicht falsch: der Endpunkt
erlaubt Überbuchung absichtlich.

**Vor dem Anlegen die Kalenderansicht abfragen.** Ein `GET` vor dem `POST`
schließt kein Zeitfenster. Zwei Anfragen können beide „frei" lesen und beide
schreiben — die Prüfung verkleinert das Fenster, sie beseitigt es nicht.

**Verteilte Sperre (Redis, Zookeeper).** Löst dasselbe Problem mit einem
weiteren Betriebsbestandteil, der ausfallen kann. Die Datenbank steht ohnehin
schon und bietet dieselbe Garantie.
