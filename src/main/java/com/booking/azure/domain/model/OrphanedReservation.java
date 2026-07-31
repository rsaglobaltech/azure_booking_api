package com.booking.azure.domain.model;

import java.time.Instant;

/**
 * Eine {@code PENDING}-Reservierung, deren Frist abgelaufen ist.
 *
 * Entsteht, wenn zwischen dem Festschreiben der Reservierung und der Antwort
 * von Microsoft Graph etwas schiefging: Zeitüberschreitung, Verbindungsabbruch
 * oder Absturz der Instanz.
 *
 * <p><b>Der Zustand sagt nichts darüber aus, ob der Termin existiert.</b>
 * Genau deshalb muss vor jeder Entscheidung bei Graph nachgesehen werden –
 * siehe {@code SlotRecoveryService}.
 *
 * @param id            Kennung der Reservierungszeile
 * @param businessId     ID der Buchungsagentur
 * @param serviceId      ID der Dienstleistung
 * @param mitarbeiterId ID des Mitarbeiters, dessen Slot belegt ist
 * @param start         Beginn (UTC, inklusive)
 * @param ende          Ende (UTC, exklusiv)
 */
public record OrphanedReservation(
        Long id,
        String businessId,
        String serviceId,
        String mitarbeiterId,
        Instant start,
        Instant ende) {
}


