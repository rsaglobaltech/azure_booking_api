# ADR-0009: Anti-Corruption Layer für Graph, Lesen bleibt direkt

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `AppointmentDraft`, `AppointmentCalendarPort`, `GraphAppointmentAdapter`

## Kontext

Das Anfrageobjekt aus der REST-Schicht wurde unverändert an Microsoft Graph
weitergereicht — samt seiner `@JsonProperty`-Annotationen. Damit war Microsofts
JSON-Format gleichzeitig das Eingangsformat dieser API:

- Ein Feld umzubenennen brach entweder die eigenen Aufrufer oder den Aufruf an
  Graph.
- Die Klasse lag in `domain/command`, also formte ein fremdes Protokoll die
  innerste Schicht.
- Interne Begriffe wie `workerNames` gingen an Graph mit hinaus.

## Entscheidung

### Schreiben geht durch das Modell

`AppointmentDraft` beschreibt in Domänenbegriffen, was geschrieben werden soll.
`GraphAppointmentAdapter` ist die **einzige** Klasse, die weiß, welches JSON
Graph erwartet, hinter `AppointmentCalendarPort`.

Ergänzende Wertobjekte `ServiceLocation` und `AppointmentCustomer` sorgen dafür,
dass nichts verlorengeht, was die Durchreichung mittrug: Adresse, Telefon,
Notizen und eine bestehende Kunden-ID.

### Der Entwurf trägt die Zone des Aufrufers

`AppointmentDraft` führt neben dem UTC-Fenster die `ZoneId`, in der der Aufrufer
den Termin ausgedrückt hat.

Das Fenster ist UTC, weil nur in dieser Form zwei Buchungen auf Überlappung
vergleichbar sind. Aber ein Kunde bucht „10:00 in Berlin", und genau das soll
der Kalender zeigen. Den Zeitpunkt beim Hinausgehen in diese Zone
zurückzurechnen reproduziert die gebuchte Uhrzeit, statt stillschweigend jeden
Termin nach UTC umzuschreiben.

### Lesen bleibt, wie es war

Abfragen liefern weiterhin `BookingAppointmentDto` unverändert aus Graph.

Sie durch Domänentypen zu führen hieße, **jedes** Feld nachzubauen, das Graph
zurückgibt — und alles nicht Modellierte verschwände stillschweigend aus den
Antworten dieser API. Das ist ein Bruch des Vertrags gegenüber bestehenden
Aufrufern, und zwar ein unbemerkter.

Befehle gehen durch das Modell, Abfragen brauchen es nicht. Ein
Domänenmodell dient dem Schutz von Invarianten; eine Leseoperation verändert
nichts und hat keine zu schützen.

### `GraphApiRequest` bleibt bestehen

Für das administrative CRUD um Betriebe, Dienste und Personal, wo dieses System
ein reiner Durchreicher ist, bleibt der HTTP-förmige Port. Er liegt jetzt aber in
`application/port/out` — einer Schicht, die Transport kennen darf — statt in der
Domäne.

## Konsequenzen

### Positiv

- Eine Änderung an Graphs Vertrag endet im Adapter.
- Eingangsformat und Ausgangsformat sind entkoppelt und je für sich änderbar.
- `workerNames` verlässt das System nicht mehr.
- Der öffentliche REST-Vertrag bleibt unangetastet.

### Negativ

- Zwei Wege für Termine: Schreiben über das Modell, Lesen direkt. Wer den Code
  liest, muss diesen Unterschied kennen — deshalb steht er in `AppointmentCalendarPort`
  ausdrücklich beschrieben.
- Für Schreibvorgänge eine zusätzliche Abbildung.
- Der Lesepfad bleibt an Graphs Format gebunden. Bewusst, nicht vergessen.

### Prüfbarkeit

Die vorhandenen WireMock-Tests **zählen** Anfragen, sie prüfen deren Rumpf nie.
Eine stillschweigend geänderte Nutzlast wäre also niemandem aufgefallen. Acht
Tests in `GraphAppointmentAdapterTest` halten die Übersetzung fest, darunter die
Zonen-Rückrechnung und das Weglassen fehlender Felder.

## Verworfene Alternativen

**Vollständige ACL in beide Richtungen.** Sauberer im Modell, aber sie zwingt zum
Nachbau des Lesevertrags; nicht modellierte Felder fielen unbemerkt aus den
Antworten.

**Nur die Befehlsklassen aus der Domäne verschieben.** Billig, aber
`GraphApiRequest` bliebe ein HTTP-förmiger Port im Modell und die Nutzlast
weiterhin von Graph diktiert. Eine halbe Maßnahme, die den Namen ACL nicht
verdient.

**Ausgehend UTC senden.** Einfacher, schreibt aber jeden Termin von der gebuchten
Ortszeit auf UTC um. Derselbe Zeitpunkt, andere Darstellung — und Bookings zeigt
an, was es bekommt.
