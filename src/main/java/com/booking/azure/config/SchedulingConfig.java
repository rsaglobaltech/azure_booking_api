package com.booking.azure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aktiviert die geplanten Aufgaben (aktuell: Wiederherstellung verwaister
 * Slot-Reservierungen).
 *
 * Onion-Architektur – Infrastrukturschicht.
 *
 * <p>Abschaltbar über {@code buchung.zeitplanung.aktiv=false}. Tests rufen den
 * Wiederherstellungslauf direkt auf; ein nebenher tickender Zeitplan würde dort
 * nur für nicht reproduzierbare Ergebnisse sorgen.
 *
 * <p><b>Betriebshinweis für mehrere Instanzen:</b> ohne weitere Maßnahme führt
 * jede Instanz den Lauf aus. Das ist derzeit unschädlich, weil der Lauf
 * idempotent ist und jede Entscheidung erst nach Rückfrage bei Graph fällt –
 * es kostet aber überflüssiges Graph-Kontingent. Bei mehr als zwei Instanzen
 * eine Auswahl per Sperre einführen (z. B. ShedLock).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "buchung.zeitplanung.aktiv", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
