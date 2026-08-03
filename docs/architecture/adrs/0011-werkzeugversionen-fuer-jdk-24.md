# ADR-0011: Werkzeugversionen für JDK 24

- **Status:** Angenommen
- **Datum:** 2026-08-03
- **Betrifft:** `pom.xml`

## Kontext

Das Projekt übersetzt nach Java 17 (`java.version`), läuft aber auf einem
installierten **JDK 24**. Spring Boot 3.2.3 verwaltet Abhängigkeiten, die diesen
Bytecode nicht kennen.

Der Zustand war lange unsichtbar, weil `target/` bereits übersetzte Klassen aus
einer früheren Umgebung enthielt: solange sich keine Quelldatei änderte,
übersetzte Maven nicht neu, und die Tests liefen gegen veraltete Klassen. Die
erste neu angelegte Datei löste eine vollständige Übersetzung aus — und damit
alle drei Probleme auf einmal.

### Die drei Befunde

**1. Byte Buddy kennt Bytecode 68 nicht.**

```
java.lang.IllegalArgumentException: Java 24 (68) is not supported by the current
version of Byte Buddy which officially supports Java 22 (66)
```

Mockito erzeugt seine Mocks darüber. `@MockBean` scheiterte, der Spring-Testkontext
kam nicht hoch, und **35 Tests fielen kaskadierend aus** — alle mit derselben
irreführenden Meldung „ApplicationContext failure threshold exceeded", die den
eigentlichen Grund verdeckte.

**2. Lombok 1.18.30 unterstützt JDK 24 nicht.**

Es scheiterte nicht, es erzeugte **schweigend nichts**: keine Getter, keine
Setter, keine Builder, kein `log`. Ergebnis waren hunderte
`cannot find symbol` in Klassen, die syntaktisch fehlerfrei waren.

**3. Seit JDK 23 läuft die Annotationsverarbeitung nicht mehr implizit.**

Auch nach der Lombok-Aktualisierung blieb der Fehler bestehen. Ursache: javac
führt Prozessoren nicht mehr allein deshalb aus, weil sie im Klassenpfad liegen.
Lombok war vorhanden und wurde nie aufgerufen.

## Entscheidung

Drei Eingriffe in `pom.xml`, jeder mit Begründung an Ort und Stelle:

```xml
<byte-buddy.version>1.17.5</byte-buddy.version>
<lombok.version>1.18.46</lombok.version>
```

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

Der ausdrückliche Prozessorpfad ist nicht nur ein Ausweg, sondern die Form, die
JDK 23+ verlangt — und die ohnehin die deutlichere ist: die Übersetzung sagt,
welche Prozessoren sie ausführt, statt es dem Klassenpfad zu überlassen.

## Konsequenzen

### Positiv

- Der Bau läuft auf JDK 24, ohne dass eine ältere JDK-Version verlangt wird.
- Der ausdrückliche Prozessorpfad ist gegen künftige JDK-Fassungen
  widerstandsfähig.
- Die Versionen sind im `pom.xml` begründet; der nächste Leser hält sie nicht
  für willkürlich.

### Negativ

- Drei überschriebene Versionen weichen von dem ab, was Spring Boot 3.2.3
  vorsieht. Bei einem Boot-Upgrade ist zu prüfen, ob sie noch nötig sind.
- Kompiliert wird nach 17, ausgeführt auf 24. Üblich und unterstützt, aber die
  beiden sollten nicht unbemerkt auseinanderdriften.

### Lehre

**Ein Bau, der gegen veraltete Klassen prüft, ist kein grüner Bau.** Die ersten
Testläufe meldeten „60 Tests, 35 Fehler" — beide Zahlen bedeutungslos, weil
teils veraltete Klassen und teils ein toter Kontext beteiligt waren. Nach der
Bereinigung waren es real 19 Tests. Vor jedem Refactoring gehört ein `clean`,
damit die Ausgangslage bekannt ist.

## Verworfene Alternativen

**Auf JDK 17 oder 21 bauen.** Vermeidet alle drei Punkte, verlangt aber von jedem
Entwickler eine bestimmte JDK-Fassung und verschiebt das Problem nur.

**`-Dnet.bytebuddy.experimental=true` setzen.** Behebt allein Punkt 1, und zwar
über einen ausdrücklich als experimentell bezeichneten Pfad.

**`<proc>full</proc>` statt Prozessorpfad.** Stellt das alte Verhalten wieder her,
sagt aber weiterhin nicht, welche Prozessoren laufen.
