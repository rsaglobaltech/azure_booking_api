package com.booking.azure.application.service;

import com.booking.azure.dto.DateTimeTimeZoneDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Umrechnung von {@link DateTimeTimeZoneDto} (lokale Zeit + Zonenangabe)
 * nach {@link Instant} in UTC.
 *
 * <h2>Warum das kritisch ist</h2>
 *
 * Dieselbe Sekunde kann als {@code 2026-08-01T10:00 Europe/Berlin} oder als
 * {@code 2026-08-01T08:00 UTC} eintreffen. Würden diese Werte als Zeichenketten
 * verglichen oder gespeichert, erkennt die {@code EXCLUDE}-Bedingung die
 * Kollision <b>nicht</b> – zwei Kunden bekämen denselben Slot.
 *
 * Regel: vor jedem Datenbankzugriff nach UTC normalisieren.
 *
 * <h2>Sommerzeitwechsel</h2>
 *
 * Bei mehrdeutigen lokalen Zeiten (Rückstellung im Oktober, 02:30 existiert
 * zweimal) wählt Java den <em>früheren</em> Zeitpunkt; bei nicht existierenden
 * Zeiten (Vorstellung im März, 02:30 existiert nicht) wird um die Lückenlänge
 * nach vorn geschoben. Das ist das Standardverhalten von
 * {@link LocalDateTime#atZone(ZoneId)} und für Terminbuchung angemessen.
 */
public final class TimeZoneConverter {

    private TimeZoneConverter() {
    }

    /**
     * Rechnet lokale Zeit plus Zonenangabe in einen UTC-Zeitpunkt um.
     *
     * @param dto Zeitangabe aus der API oder aus einer Graph-Antwort
     * @return Zeitpunkt in UTC
     * @throws IllegalArgumentException bei fehlenden oder unlesbaren Angaben
     */
    public static Instant toInstant(DateTimeTimeZoneDto dto) {
        if (dto == null || dto.getDateTime() == null || dto.getDateTime().isBlank()) {
            throw new IllegalArgumentException("dateTime fehlt");
        }

        String rohzeit = dto.getDateTime().trim();

        // Fall 1: der Wert trägt bereits einen Offset ("...Z" oder "...+02:00").
        // Dann ist er absolut und die Zonenangabe daneben ist irrelevant.
        try {
            return OffsetDateTime.parse(rohzeit).toInstant();
        } catch (DateTimeParseException nichtAbsolut) {
            // weiter mit Fall 2
        }

        // Fall 2: lokale Zeit ohne Offset – die Zonenangabe entscheidet.
        ZoneId zone = zoneErmitteln(dto.getTimeZone());
        try {
            return LocalDateTime.parse(rohzeit).atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "dateTime '%s' ist kein gültiger ISO-8601-Zeitstempel".formatted(rohzeit), ex);
        }
    }

    private static ZoneId zoneErmitteln(String zonenName) {
        if (zonenName == null || zonenName.isBlank()) {
            throw new IllegalArgumentException(
                    "timeZone fehlt. Ohne Zonenangabe ist die lokale Zeit nicht eindeutig.");
        }
        try {
            return ZoneId.of(zonenName.trim());
        } catch (Exception ex) {
            // Microsoft Graph liefert je nach Endpunkt auch Windows-Zonennamen
            // ("W. Europe Standard Time") statt IANA-Namen ("Europe/Berlin").
            // Diese werden hier bewusst nicht übersetzt: eine unvollständige
            // Abbildung wäre gefährlicher als eine klare Fehlermeldung.
            throw new IllegalArgumentException(
                    "timeZone '%s' ist kein bekannter IANA-Zonenname (erwartet z. B. 'Europe/Berlin' oder 'UTC')"
                            .formatted(zonenName), ex);
        }
    }
}


