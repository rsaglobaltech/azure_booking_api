# Architekturentscheidungen (ADRs)

Ein ADR (Architecture Decision Record) hält eine tragende Entscheidung fest:
den Zusammenhang, in dem sie fiel, die Entscheidung selbst, ihre Folgen und die
verworfenen Alternativen.

**Wozu das gut ist.** Der Code zeigt, *was* gebaut wurde. Er zeigt nicht, was
stattdessen erwogen und aus welchem Grund verworfen wurde. Ohne diese Notiz wird
eine bewusste Entscheidung später für ein Versehen gehalten und „aufgeräumt" —
mitsamt dem Problem, das sie gelöst hat.

## Verzeichnis

| Nr. | Titel | Status |
|-----|-------|--------|
| [0001](0001-hexagonale-architektur.md) | Hexagonale Architektur mit Ports und Adaptern | Angenommen |
| [0002](0002-eigene-slot-reservierung-als-autoritaet.md) | Eigene Slot-Reservierung als Autorität für den gegenseitigen Ausschluss | Angenommen |
| [0003](0003-pessimistisches-sperren-statt-exclude.md) | Pessimistisches Sperren statt EXCLUDE-Bedingung | Angenommen |
| [0004](0004-kompensation-unterscheidet-ablehnung-von-schweigen.md) | Kompensation unterscheidet Ablehnung von Schweigen | Angenommen |
| [0005](0005-wiederherstellung-fragt-zuerst-bei-graph-nach.md) | Wiederherstellung fragt zuerst bei Graph nach | Angenommen |
| [0006](0006-booking-als-aggregat-mit-eigener-identitaet.md) | Booking als Aggregat mit eigener Identität | Angenommen |
| [0007](0007-domaenenereignisse-mit-eigenem-in-memory-bus.md) | Domänenereignisse mit eigenem In-Memory-Bus | Angenommen |
| [0008](0008-kundendaten-werden-nicht-lokal-gespeichert.md) | Kundendaten werden nicht lokal gespeichert | Angenommen |
| [0009](0009-anti-corruption-layer-fuer-graph.md) | Anti-Corruption Layer für Graph, Lesen bleibt direkt | Angenommen |
| [0010](0010-ubiquitous-language-auf-englisch.md) | Ubiquitous Language auf Englisch | Angenommen |
| [0011](0011-werkzeugversionen-fuer-jdk-24.md) | Werkzeugversionen für JDK 24 | Angenommen |

## Status

- **Vorgeschlagen** — zur Diskussion gestellt, noch nicht wirksam
- **Angenommen** — gilt und ist im Code umgesetzt
- **Abgelöst durch ADR-XXXX** — überholt; der Nachfolger nennt den Grund
- **Zurückgezogen** — verworfen, ohne Nachfolger

Ein angenommener ADR wird **nicht** nachträglich umgeschrieben. Ändert sich die
Entscheidung, entsteht ein neuer ADR, und der alte bekommt den Status
*Abgelöst*. Die Spur, wie das System zu seiner heutigen Form kam, ist mehr wert
als ein aufgeräumtes Verzeichnis.

## Format

Angelehnt an [MADR](https://adr.github.io/madr/): Kontext, Entscheidung,
Konsequenzen, verworfene Alternativen. Neue ADRs bekommen die nächste freie
Nummer; Nummern werden nie wiederverwendet.
