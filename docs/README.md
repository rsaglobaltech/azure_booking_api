# Dokumentation — Azure Booking API

Spring-Boot-REST-API für Microsoft Bookings über Microsoft Graph, gebaut nach
hexagonaler Architektur mit einem Domänenmodell aus Aggregaten, Wertobjekten
und Domänenereignissen.

## Aufbau

```
docs/
├── README.md                      # dieses Verzeichnis
├── architecture/
│   └── adrs/                      # Architekturentscheidungen (ADRs)
├── ARCHITEKTUR.md                 # Überblick über Schichten und Bausteine
├── PLAN-DDD.md                    # Umbauplan auf DDD, mit Stand je Phase
├── PLAN-COLISION-RESERVAS.md      # Verfahren gegen Doppelbuchungen
├── GRAPH-API-INTEGRATION-BERICHT.md
└── GUIA_RESERVAS.md
```

## Architekturentscheidungen

Jede tragende Entscheidung liegt als eigener ADR in
[`architecture/adrs/`](architecture/adrs/README.md). Ein ADR hält fest, **warum**
etwas so ist — nicht wie es funktioniert. Das Wie steht im Code und in den
Plandokumenten.

Der Einstieg lohnt in dieser Reihenfolge:

1. [ADR-0001 — Hexagonale Architektur](architecture/adrs/0001-hexagonale-architektur.md)
2. [ADR-0002 — Eigene Slot-Reservierung als Autorität](architecture/adrs/0002-eigene-slot-reservierung-als-autoritaet.md)
3. [ADR-0006 — Booking als Aggregat mit eigener Identität](architecture/adrs/0006-booking-als-aggregat-mit-eigener-identitaet.md)

## Die eine Regel, die alles andere erklärt

Microsoft Graph kann den gegenseitigen Ausschluss nicht garantieren: es bietet
weder Sperren noch Transaktionen noch bedingte Schreibvorgänge auf
`bookingAppointment`, und der verwendete Endpunkt erlaubt Überbuchung bewusst.

Fast jede Eigenheit dieses Systems — die eigene Reservierungstabelle, das
pessimistische Sperren, der Wiederherstellungsjob, die Unterscheidung zwischen
„Graph hat abgelehnt" und „Graph hat nicht geantwortet" — folgt aus diesem einen
Umstand. Wer das im Kopf hat, versteht den Rest.

## Sprache

Der Code spricht **Englisch**: Bezeichner, Methoden, Tests und die Javadoc der
im DDD-Umbau geschriebenen Klassen. Die Dokumentation und die ADRs sind
**deutsch**. Zur Begründung und zu der einen bewussten Ausnahme siehe
[ADR-0010](architecture/adrs/0010-ubiquitous-language-auf-englisch.md).
