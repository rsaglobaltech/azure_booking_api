package com.booking.azure.infrastructure.persistence;

import com.booking.azure.domain.model.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring-Data-Repository für Slot-Reservierungen.
 *
 * Onion-Architektur – Infrastrukturschicht.
 */
public interface SlotReservationRepository extends JpaRepository<SlotReservationEntity, Long> {

    /** Aktive Reservierungen zu einem Graph-Termin (für Stornierung und Umbuchung). */
    List<SlotReservationEntity> findByGraphAppointmentIdAndStateIn(
            String graphAppointmentId, Collection<SlotStatus> zustaende);

    /**
     * Verwaiste Reservierungen für den Wiederherstellungsjob.
     *
     * <p><b>Achtung:</b> Diese Zeilen dürfen nicht blind freigegeben werden.
     * Das Ablaufen einer Zeile bricht einen laufenden HTTP-Aufruf nicht ab –
     * eine blinde Freigabe erzeugt genau die Doppelbuchung, die sie verhindern
     * soll. Vor der Freigabe ist über {@code GET /calendarView} zu prüfen, ob
     * der Termin in Graph doch entstanden ist.
     * Siehe docs/PLAN-COLISION-RESERVAS.md §2.5.
     */
    List<SlotReservationEntity> findByStateAndExpiresAtBefore(SlotStatus zustand, Instant zeitpunkt);
}
