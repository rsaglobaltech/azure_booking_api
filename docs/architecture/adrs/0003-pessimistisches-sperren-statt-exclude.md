# ADR-0003: Pessimistisches Sperren statt EXCLUDE-Bedingung

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `BookingJpaAdapter#store`, `SpringDataStaffRepository#lockByMsStaffMemberId`
- **Ergänzt:** [ADR-0002](0002-eigene-slot-reservierung-als-autoritaet.md)

## Kontext

Der ursprüngliche Entwurf (siehe `PLAN-COLISION-RESERVAS.md`) setzte auf
PostgreSQL:

```sql
CONSTRAINT ex_slot_overlap EXCLUDE USING gist (
    staff_member_id WITH =,
    tsrange(start_utc, end_utc) WITH &&
) WHERE (state IN ('PENDING', 'CONFIRMED'))
```

Die Datenbank selbst weist überlappende Zeilen ab — die Regel steht im Schema,
und keine Anwendung kann an ihr vorbei schreiben.

Mit der Migration nach Oracle entfiel diese Möglichkeit: Oracle kennt weder
`EXCLUDE USING gist` noch einen gleichwertigen Bereichs-Ausschluss.

## Entscheidung

Der Ausschluss wird programmatisch erzwungen, in **einer** Transaktion und in
dieser Reihenfolge:

1. **Sperren** — `PESSIMISTIC_WRITE` auf die Zeile des Mitarbeiters. Damit
   werden alle Anfragen für denselben Mitarbeiter serialisiert.
2. **Prüfen** — `countOverlappingReservations` auf blockierende Reservierungen
   im angefragten Fenster.
3. **Handeln** — bei Überlappung Abbruch mit `SlotConflictException` (HTTP 409),
   sonst einfügen und festschreiben, wodurch die Sperre fällt.

Die Sperre **vor** der Prüfung ist der ganze Punkt. Ohne sie könnten zwei
Transaktionen beide „kein Konflikt" lesen und anschließend beide einfügen.

`saveAllAndFlush` statt `saveAll`: der Schreibvorgang muss die Datenbank
erreichen, bevor die Sperre mit dem Commit fällt.

Der Vergleich ist halboffen (`start < :ende AND ende > :start`) und stimmt damit
exakt mit `TimeWindow.overlaps` überein — aneinander anschließende Termine
kollidieren nicht.

## Konsequenzen

### Positiv

- Läuft auf Oracle, ohne Erweiterungen.
- Die Sperre liegt auf dem Mitarbeiter, nicht auf der Reservierungstabelle:
  Buchungen für verschiedene Mitarbeiter laufen weiterhin parallel.
- Durch `AppointmentConcurrencyTest` gegen eine echte Datenbank abgesichert.

### Negativ

- **Die Regel steht nicht mehr im Schema.** Sie gilt nur für Schreibvorgänge,
  die durch diesen Adapter gehen. Ein Skript, das direkt in `slot_reservation`
  schreibt, erzeugt Doppelbuchungen, ohne dass die Datenbank widerspricht. Das
  ist der eigentliche Preis dieser Entscheidung.
- Die Sperre setzt voraus, dass zu jedem `staff_member_id` eine Zeile in
  `staff_mapping` existiert.
- Bei vielen gleichzeitigen Anfragen auf denselben Mitarbeiter entsteht eine
  Warteschlange — fachlich korrekt, aber sichtbar in der Antwortzeit.

### Folge für das Modell

Diese Invariante liegt zwischen verschiedenen Aggregaten und ist im Speicher
nicht prüfbar: ein `Booking` weiß nichts von Buchungen, die es nie geladen hat.
Sie bleibt deshalb bewusst im Adapter und ist dort als solche dokumentiert —
siehe [ADR-0006](0006-booking-als-aggregat-mit-eigener-identitaet.md).

## Verworfene Alternativen

**Optimistisches Sperren mit Wiederholung.** Weniger Kontention, aber die
Kollision fällt erst beim Schreiben auf. Bei einem Fremdsystemaufruf dazwischen
wird die Wiederholung erheblich aufwendiger.

**Eindeutiger Index auf gerasterte Zeitfenster.** Erzwingt ein festes Raster
(etwa 15 Minuten) und bricht bei abweichenden Dienstleistungsdauern.

**`SERIALIZABLE` als Isolationsstufe.** Verlagert das Problem auf die Datenbank,
zum Preis von Serialisierungsfehlern im gesamten System statt gezielter Sperren
auf einer Zeile.
