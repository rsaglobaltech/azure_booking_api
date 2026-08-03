# ADR-0007: Domänenereignisse mit eigenem In-Memory-Bus

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `DomainEvent`, `AggregateRoot`, `DomainEventPublisher`, `InMemoryEventBus`

## Kontext

Nach dem Bestätigen einer Buchung sollen Kunde und Agentur eine E-Mail
erhalten. Bisher rief der Anwendungsfall den Benachrichtigungsport direkt auf,
umschlossen von einem `try/catch`, das jeden Fehler verschluckte:

```java
try {
    // ... BookingDetails zusammenbauen ...
    notificationPort.notifyBookingConfirmed(details);
} catch (Exception e) {
    log.error("Failed to trigger notifications", e);
}
```

Die Buchungslogik musste also wissen, dass es E-Mails gibt, **und** sich
zugleich gegen sie verteidigen. Jede weitere Reaktion — Kalendereintrag, Webhook,
Kennzahl — hätte denselben Anwendungsfall erneut geändert.

## Entscheidung

Aggregate **melden, was geschehen ist**; wer darauf reagieren will, meldet sich
an.

```
domain/event/DomainEvent          eventId, occurredOn, aggregateId
domain/model/AggregateRoot        registerEvent(), pullEvents()
domain/port/out/DomainEventPublisher
infrastructure/adapter/out/event/InMemoryEventBus
```

Ereignisse: `SlotsReserved`, `BookingConfirmed`, `BookingReleased`.

### Das Aggregat sammelt, es veröffentlicht nicht

Ein Aggregat, das selbst veröffentlichte, bräuchte den Publisher als
Abhängigkeit und würde Tatsachen melden, bevor sie dauerhaft sind — eine
Bestätigung, die anschließend nicht festgeschrieben wird. Stattdessen sammelt
es, und der Aufrufer leert die Liste, **nachdem** der Schreibvorgang gelungen
ist.

`pullEvents()` leert und löscht dabei. Ein doppelt zugestelltes Ereignis
schickte dem Kunden eine zweite Bestätigungsmail.

### Eigenes Register statt Spring

`InMemoryEventBus` führt eine eigene
`Map<Class<? extends DomainEvent>, List<DomainEventHandler<?>>>`, befüllt beim
Start aus dem `ApplicationContext`.

Spring zu umhüllen wäre kürzer gewesen, zieht das Framework aber in die Form der
Domäne: Handler würden `@EventListener`-Beans, Ereignisse müssten Springs
Verträge erfüllen, und das Prüfen einer Reaktion bräuchte einen Kontext. So
deklariert die Domäne `DomainEventHandler`, und nur dieser Adapter weiß, dass es
Spring gibt.

### Zustellung

Synchron, im veröffentlichenden Thread, mit `try/catch` **je Handler**. Ein
scheiternder Handler wird protokolliert und übersprungen: E-Mails sind eine
Reaktion auf eine Buchung, nicht Teil von ihr, und ein nicht erreichbarer
Mailserver darf keinen bestätigten Termin zurücknehmen.

## Konsequenzen

### Positiv

- Der Anwendungsfall kennt die Benachrichtigung nicht mehr; das verschluckende
  `try/catch` ist verschwunden.
- Weitere Reaktionen kommen als neue Handler hinzu, ohne die Buchungslogik zu
  berühren.
- Die Domäne bleibt frei von Spring; ein Wechsel auf einen echten Broker ist ein
  Adapterwechsel.
- `InMemoryEventBusTest` prüft Zustellung und Fehlerisolation ohne Kontext.

### Negativ

- **Ereignisse leben nur im Speicher.** Stirbt der Prozess zwischen Commit und
  Zustellung, ist das Ereignis verloren — ohne Wiederholung, ohne Nachlauf.
  Bewusst hingenommen, solange Benachrichtigungen der einzige Abonnent sind.
- Synchrone Zustellung verlängert die Anfrage um die Dauer der Handler. Der
  E-Mail-Adapter ist deshalb `@Async`.
- Ein eigenes Register bedeutet eigenen Code für etwas, das ein Framework
  mitbringt.

### Offene Schuld

Ein **Transactional Outbox** ist die Antwort, sobald etwas Tragendes mithört:
Ereignis und Zustandsänderung in derselben Transaktion schreiben, separat
zustellen. Bis dahin bleibt der Verlust im Absturzfall dokumentiert und
akzeptiert.

## Verworfene Alternativen

**Springs `ApplicationEventPublisher` umhüllen.** Kürzer, aber Framework in der
Domänenform, wie oben beschrieben.

**Direkte Aufrufe beibehalten.** Der Ausgangszustand.

**Sofort ein Broker (Kafka, Service Bus).** Betriebsaufwand für einen einzigen
Abonnenten im selben Prozess. Der Port existiert, damit dieser Schritt später
klein bleibt.

**Asynchrone Zustellung im Bus.** Verlagert die Fehlerbehandlung in einen
anderen Thread, ohne die Dauerhaftigkeit zu verbessern. Das Nebenläufige gehört
in den Adapter, der es braucht.
