# ADR-0006: Booking als Aggregat mit eigener Identität

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `Booking`, `SlotReservation`, `BookingRepository`, Migration `V3__booking_id.sql`

## Kontext

Vor dem Umbau war `SlotReservation` eine JPA-Entität in der Infrastrukturschicht,
und die Zustandsübergänge fanden im Adapter statt:

```java
zeile.setState(SlotStatus.CONFIRMED);
zeile.setGraphAppointmentId(graphTerminId);
```

Daraus folgten zwei Probleme:

1. **Nichts verhinderte einen unsinnigen Übergang.** Eine freigegebene Zeile
   ließ sich erneut bestätigen — der Zustand, in dem das System glaubt, einen
   Slot zu halten, den es weggegeben hat.
2. **Reservierungen einer Buchung kannten einander nicht.** Sie teilten nur
   Betrieb, Dienst, Fenster und Anlagezeitpunkt. Die Zusammengehörigkeit war
   Vermutung, keine Tatsache. Damit ließ sich die Regel „ganz oder gar nicht"
   nicht durchsetzen, und ein Domänenereignis hatte kein Aggregat, auf das es
   zeigen konnte.

## Entscheidung

**`Booking` wird Aggregatwurzel über die `SlotReservation`-Entitäten**, die es
hält.

- Die Zustandsübergänge von `SlotReservation` sind **paketprivat**. Nur die
  Wurzel darf sie auslösen; von außen ist eine einzelne Reservierung nicht
  erreichbar. Genau das macht „ganz oder gar nicht" durchsetzbar.
- `confirm(...)` auf einer freigegebenen Buchung wirft
  `IllegalBookingStateException`, ebenso das Binden einer zweiten, abweichenden
  Termin-ID. Erneutes Bestätigen mit derselben ID ist zulässig — der
  Wiederherstellungsjob kann mit einer laufenden Anfrage zusammentreffen.
- `release()` ist idempotent; Kompensationspfade dürfen mehrfach laufen.

**Das Aggregat bekommt eine eigene Identität.** `V3__booking_id.sql` fügt
`booking_id` hinzu — additiv: Spalte nullable anlegen, bestehende Zeilen
befüllen, dann auf `NOT NULL` setzen. Bestandszeilen erhalten je eine eigene
Identität, weil sie sich nachträglich nicht gruppieren lassen.

Die Identität entsteht **lokal und vor** dem Graph-Aufruf. Eine Buchung
existiert — und hält Slots — ab dem Moment der Anfrage, lange bevor sie eine
`AppointmentId` hat.

### Was nicht zum Aggregat gehört

Die Regel, dass zwei **verschiedene** Buchungen sich nicht überlappen dürfen,
überschreitet Aggregatgrenzen und ist im Speicher nicht prüfbar: ein `Booking`
weiß nichts von Buchungen, die es nie geladen hat. Sie bleibt in der Datenbank,
durchgesetzt durch [ADR-0003](0003-pessimistisches-sperren-statt-exclude.md),
und ist im Aggregat als bewusste Auslassung dokumentiert.

## Konsequenzen

### Positiv

- Unsinnige Übergänge sind unmöglich statt unwahrscheinlich, und durch
  `BookingTest` ohne Datenbank abgesichert.
- Die Wiederherstellung entscheidet über die gesamte Buchung; halb bestätigte
  Buchungen kann es nicht mehr geben.
- Domänenereignisse haben ein Aggregat, auf das sie sich beziehen — Voraussetzung
  für [ADR-0007](0007-domaenenereignisse-mit-eigenem-in-memory-bus.md).

### Negativ

- Eine Schemamigration auf einer bestehenden Tabelle. Additiv und rückgängig zu
  machen, aber eine Migration.
- Eine zusätzliche Abbildungsschicht (`BookingMapper`) zwischen Aggregat und
  Zeilen.
- Bestandszeilen aus der Zeit vor der Migration tragen je eine eigene
  Buchungs-ID, auch wenn sie ursprünglich zusammengehörten. Nicht reparabel und
  ohne praktische Folgen, da diese Buchungen abgeschlossen sind.

### Besonderheit: Umbuchung

`Booking.rescheduleOf()` erzeugt die Ersatzbuchung mit bereits bekannter
`AppointmentId`, hält die Reservierungen aber auf `PENDING`. Als `CONFIRMED`
eingefügt wären sie für den Wiederherstellungsjob unsichtbar — er sieht nur
`PENDING` — und der Slot bliebe für immer blockiert, wenn der Prozess mitten in
der Umbuchung stirbt.

## Verworfene Alternativen

**Ohne eigene Identität, Zusammengehörigkeit aus den Spalten ableiten.** Spart
die Migration, bleibt aber Vermutung, und lässt Ereignisse ohne Bezugspunkt.

**`AppointmentId` als Identität des Aggregats.** Existiert erst, nachdem Graph
geantwortet hat — genau in der Phase, in der die Buchung bereits Slots hält,
gäbe es keine Identität.

**Übergänge weiter im Adapter.** Der Ausgangszustand: die Regeln lagen entfernt
von den Daten, die sie einschränken, und ließen sich nur mit Datenbank prüfen.
