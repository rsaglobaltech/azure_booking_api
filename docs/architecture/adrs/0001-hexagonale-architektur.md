# ADR-0001: Hexagonale Architektur mit Ports und Adaptern

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** gesamte Codebasis

## Kontext

Das System vermittelt zwischen einer eigenen REST-API und Microsoft Bookings
über Microsoft Graph. Beide Seiten gehören uns nicht: die Graph-API ändert sich
nach Microsofts Zeitplan, und die eigene API hat Aufrufer, die nicht bei jeder
internen Umbenennung mitziehen können.

Fachlich trägt das System nur wenig eigene Regeln — aber die, die es trägt, sind
die teuersten: der gegenseitige Ausschluss von Terminen. Ein Fehler darin
erzeugt Doppelbuchungen, die niemand bemerkt, bis zwei Kunden vor derselben Tür
stehen.

Genau diese Regeln dürfen nicht in einem Framework-Detail, einem JPA-Adapter
oder einem HTTP-Controller versteckt liegen, wo sie nur mit laufendem Container
prüfbar sind.

## Entscheidung

Vier Schichten, mit einer Abhängigkeitsrichtung, die nur nach innen zeigt:

```
infrastructure  →  application  →  domain
      ↑                               (kennt niemanden)
  (Adapter)
```

- **`domain`** — Aggregate, Wertobjekte, Ereignisse, fachliche Ausnahmen und die
  ausgehenden Ports, die dem Modell wirklich gehören. Ohne Spring, ohne JPA,
  ohne Jackson, ohne Bean Validation.
- **`application`** — Anwendungsfälle, eingehende Ports, Befehlsobjekte und die
  ausgehenden Ports, die Transport beschreiben.
- **`infrastructure`** — Adapter: REST-Controller, JPA, Graph-Client, E-Mail,
  Ereignisbus.

Die Regeln werden nicht auf Zuruf eingehalten, sondern von ArchUnit geprüft
(`HexagonalArchitectureTest`). Sechs Regeln, unter anderem: die Domäne hängt
nicht von Spring, nicht von `jakarta.persistence`, nicht von Jackson und nicht
vom Transport-DTO-Paket ab.

## Konsequenzen

### Positiv

- Die Buchungsregeln sind ohne Spring-Kontext und ohne Datenbank prüfbar. Die
  44 Unit-Tests des Modells laufen in Millisekunden statt in 30 Sekunden.
- Ein Wechsel der Persistenz oder des Kalenderanbieters trifft Adapter, nicht
  das Modell.
- Verstöße scheitern im Build, nicht erst im Review.

### Negativ

- Mehr Klassen und mehr Abbildung zwischen Schichten. Ein Feld, das durch alle
  Schichten reicht, wird an drei Stellen genannt.
- Die Trennung ist nur so viel wert wie die Disziplin dahinter. Deshalb die
  ArchUnit-Regeln — eine Konvention ohne Test ist eine Absichtserklärung.

### Bekannte Einschränkung

ArchUnit sieht nur **direkte** Abhängigkeiten. Eine Domänenklasse, die ein
Jackson-annotiertes DTO referenzierte, bestand die Jackson-Regel, obwohl das
JSON-Format sie weiterhin formte — die Annotation lag einen Sprung entfernt.
Deshalb gibt es zusätzlich die Regel gegen das DTO-Paket selbst; sie schließt
die Lücke, die die anderen nicht ausdrücken können.

## Verworfene Alternativen

**Klassische Schichtung (Controller → Service → Repository).** Kürzer, und für
reine CRUD-Durchreichung völlig ausreichend. Verworfen, weil die
Reservierungslogik dann im Service zwischen Orchestrierung und Persistenz liegt
— genau der Zustand, aus dem dieser Umbau herausführt.

**Alles in einer Schicht.** Bei diesem Umfang verteidigbar, solange nur
durchgereicht wird. Sobald eigene Invarianten dazukommen, verliert man die
Möglichkeit, sie isoliert zu prüfen.
