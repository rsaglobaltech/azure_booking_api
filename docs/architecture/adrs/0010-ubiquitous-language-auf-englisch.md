# ADR-0010: Ubiquitous Language auf Englisch

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** alle Bezeichner in `src/`

## Kontext

Die Codebasis mischte drei Sprachen, oft in derselben Datei und gelegentlich im
selben Namen:

```java
public int freigebenNachTerminId(String graphTerminId);
List<OrphanedReservation> verwaisteFinden(Instant vor);
List<BookingAppointmentDto> kalenderAnsichtAbrufen(String agencyName, ...);
private List<String> mitarbeiterIds;
```

Eine gemeinsame Sprache zwischen Fachlichkeit und Code ist keine Kosmetik,
sondern der Kern von Domain Driven Design: dieselbe Sache muss überall gleich
heißen, sonst diskutieren Beteiligte aneinander vorbei und der Code beschreibt
etwas anderes als die Fachlichkeit.

`slotReservation.freigebenNachTerminId(...)` in einer Methode namens
`cancelAppointment` benennt zweimal dieselbe Sache verschieden.

## Entscheidung

**Bezeichner sind englisch.** Klassen, Methoden, Felder, Parameter, lokale
Variablen und Testnamen.

**Dokumentation und ADRs sind deutsch.** Sie richten sich an das Team, nicht an
den Übersetzer.

Die Javadoc der im Umbau geschriebenen Klassen ist englisch, damit sie zu den
Bezeichnern passt, die sie beschreibt. Ältere deutsche Javadoc in Infrastruktur,
DTOs und Tests steht noch aus.

Beispiele der Umbenennung:

| vorher | nachher |
|--------|---------|
| `freigebenNachTerminId` | `releaseByAppointmentId` |
| `verwaisteFinden` | `findOrphaned` |
| `kalenderAnsichtAbrufen` | `getCalendarView` |
| `mitarbeiterIds` | `staffMemberIds` |
| `anschliessendeTermineSindErlaubt` | `backToBackAppointmentsAreAllowed` |

Testnamen sind Dokumentation: `backToBackAppointmentsAreAllowed` nennt die
Regel, die der Test festhält — `anschliessendeTermineSindErlaubt` tat das nur für
deutschsprachige Leser.

## Die Ausnahme

`GlobalExceptionHandler.Fehlerantwort` bleibt deutsch, mitsamt den Komponenten
`nachricht` und `zeitstempel`.

Jackson serialisiert einen Record über die Namen seiner Komponenten. Dieser Typ
erzeugt also:

```json
{ "status": 409, "nachricht": "...", "zeitstempel": "..." }
```

Das ist der Fehlerrumpf, den **jeder bestehende Aufrufer bereits auswertet**.
`nachricht` in `message` umzubenennen bricht sie stillschweigend — kein
Übersetzungsfehler, sondern ein Vertragsbruch.

Das ist eine Entscheidung über eine API-Version, kein Refactoring. Die Klasse
sagt das an Ort und Stelle, damit niemand die Inkonsistenz für ein Versehen hält
und sie „behebt".

## Konsequenzen

### Positiv

- Ein Begriff, ein Name, überall.
- Kein Wechsel der Sprache mitten in einem Aufrufpfad.
- Die Namensgebung passt zu Java, Spring und Graph, die ohnehin englisch sind.

### Negativ

- Ein großer, streuender Umbenennungs-Diff, der die Geschichte einzelner Zeilen
  in `git blame` überdeckt.
- Eine bewusste Inkonsistenz bei `Fehlerantwort`, die ohne den Kommentar daneben
  wie ein Versehen aussähe.
- Deutsche Prosa in älterer Javadoc steht noch aus; bis dahin sind Bezeichner
  und Kommentare in denselben Dateien unterschiedlich sprachig.

## Verworfene Alternativen

**Alles auf Deutsch.** Ebenso konsistent und passend zu den Kommentaren. Verworfen,
weil die umgebenden Werkzeuge englisch sind: `getCalendarView` neben
`ResponseEntity` liest sich besser als `kalenderAnsichtAbrufen`.

**So lassen, nur Neues auf Englisch.** Der kleinste Diff und die schlechteste
gemeinsame Sprache: der Zustand, aus dem dieser ADR herausführt.

**`Fehlerantwort` umbenennen und das JSON per `@JsonProperty` festhalten.**
Bezeichner englisch, Vertrag stabil — aber das Deutsche wäre dauerhaft im
Vertrag verankert, während der Code so täte, als wäre es weg. Die ehrliche
Variante ist, den Namen stehen zu lassen und den Grund zu nennen.
