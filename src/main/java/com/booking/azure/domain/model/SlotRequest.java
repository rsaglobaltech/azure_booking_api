package com.booking.azure.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Anfrage zur Reservierung eines Zeitfensters für einen oder mehrere Mitarbeiter.
 *
 * Onion-Architektur – Domänenschicht: reines Wertobjekt, keine Abhängigkeit
 * auf JPA, Spring oder Jackson.
 *
 * <p><b>Zeitangaben sind immer UTC.</b> Die Umrechnung aus lokaler Zeit plus
 * Zonenangabe geschieht in der Anwendungsschicht, bevor dieses Objekt entsteht.
 * Ohne diese Normalisierung würden {@code 10:00 Europe/Berlin} und
 * {@code 08:00 UTC} als verschiedene Slots gelten und die Kollision bliebe
 * unerkannt.
 *
 * @param businessId      ID der Buchungsagentur (bookingBusiness)
 * @param serviceId       ID der Dienstleistung (bookingService)
 * @param mitarbeiterIds Alle zugewiesenen Mitarbeiter; für jeden entsteht eine
 *                       eigene Reservierungszeile. Ist einer davon belegt,
 *                       scheitert die gesamte Reservierung.
 * @param start          Beginn (UTC, inklusive)
 * @param ende           Ende (UTC, exklusiv)
 */
public record SlotRequest(
        String businessId,
        String serviceId,
        List<String> mitarbeiterIds,
        Instant start,
        Instant ende) {

    public SlotRequest {
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("businessId ist erforderlich");
        }
        if (mitarbeiterIds == null || mitarbeiterIds.isEmpty()) {
            throw new IllegalArgumentException("mindestens ein Mitarbeiter ist erforderlich");
        }
        if (start == null || ende == null) {
            throw new IllegalArgumentException("start und ende sind erforderlich");
        }
        if (!ende.isAfter(start)) {
            throw new IllegalArgumentException(
                    "ende muss nach start liegen (start=" + start + ", ende=" + ende + ")");
        }
        mitarbeiterIds = List.copyOf(mitarbeiterIds);
    }
}


