package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.SlotRequest;
import com.booking.azure.domain.model.SlotStatus;
import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.port.out.SlotReservationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Infrastruktur-Adapter: implementiert {@link SlotReservationPort} über PostgreSQL.
 *
 * <h2>How Collisions Are Prevented</h2>
 * 
 * Originally designed with PostgreSQL `EXCLUDE` constraints, this logic has been 
 * migrated to Oracle DB using Pessimistic Write Locks on the `StaffMapping` entity. 
 * See the `speichern` method for the detailed implementation of the collision algorithm.
 */
@Slf4j
@Component
public class SlotReservationJpaAdapter implements SlotReservationPort {

    private static final List<SlotStatus> BLOCKIEREND =
            List.of(SlotStatus.PENDING, SlotStatus.CONFIRMED);

    private final SlotReservationRepository repository;
    private final SpringDataStaffRepository staffRepository;
    private final Duration reservierungsTtl;

    public SlotReservationJpaAdapter(
            SlotReservationRepository repository,
            SpringDataStaffRepository staffRepository,
            @Value("${buchung.slot-reservierung.ttl:PT90S}") Duration reservierungsTtl) {
        this.repository = repository;
        this.staffRepository = staffRepository;
        this.reservierungsTtl = reservierungsTtl;
    }

    @Override
    @Transactional
    public List<Long> reserve(SlotRequest request) {
        List<SlotReservationEntity> neu = request.mitarbeiterIds().stream()
                .map(mitarbeiterId -> neueReservierung(request, mitarbeiterId))
                .toList();

        return speichern(neu, request);
    }

    @Override
    @Transactional
    public void confirm(List<Long> reservierungsIds, String graphTerminId) {
        Instant jetzt = Instant.now();
        List<SlotReservationEntity> zeilen = repository.findAllById(reservierungsIds);

        for (SlotReservationEntity zeile : zeilen) {
            zeile.setState(SlotStatus.CONFIRMED);
            zeile.setGraphAppointmentId(graphTerminId);
            zeile.setUpdatedAt(jetzt);
        }
        repository.saveAll(zeilen);

        log.debug("{} Reservierung(en) bestätigt für Graph-Termin {}", zeilen.size(), graphTerminId);
    }

    @Override
    @Transactional
    public void release(List<Long> reservierungsIds) {
        Instant jetzt = Instant.now();
        List<SlotReservationEntity> zeilen = repository.findAllById(reservierungsIds);

        for (SlotReservationEntity zeile : zeilen) {
            zeile.setState(SlotStatus.RELEASED);
            zeile.setUpdatedAt(jetzt);
        }
        repository.saveAll(zeilen);

        log.debug("{} Reservierung(en) freigegeben", zeilen.size());
    }

    @Override
    @Transactional
    public int freigebenNachTerminId(String graphTerminId) {
        List<SlotReservationEntity> zeilen =
                repository.findByGraphAppointmentIdAndStateIn(graphTerminId, BLOCKIEREND);
        freigebenIntern(zeilen);

        log.debug("{} Reservierung(en) freigegeben für Graph-Termin {}", zeilen.size(), graphTerminId);
        return zeilen.size();
    }

    /**
     * Freigabe des alten und Belegung des neuen Fensters in <b>einer</b> Transaktion.
     *
     * Getrennte Transaktionen wären fehlerhaft: dazwischen könnte eine andere
     * Anfrage den alten Slot übernehmen, und scheiterte danach die Neubelegung,
     * wäre der alte Slot bereits unwiederbringlich verloren.
     */
    @Override
    @Transactional
    public List<Long> reschedule(String graphTerminId, SlotRequest neuesFenster) {
        List<SlotReservationEntity> alt =
                repository.findByGraphAppointmentIdAndStateIn(graphTerminId, BLOCKIEREND);
        freigebenIntern(alt);

        // Ohne dieses Flush sieht die EXCLUDE-Bedingung die Freigabe noch nicht.
        // Eine Umbuchung auf ein überlappendes Fenster desselben Termins
        // (z. B. 10:00–11:00 → 10:30–11:30) kollidierte sonst mit sich selbst.
        repository.flush();

        List<SlotReservationEntity> neu = neuesFenster.mitarbeiterIds().stream()
                .map(mitarbeiterId -> neueReservierung(neuesFenster, mitarbeiterId))
                .toList();
        neu.forEach(zeile -> zeile.setGraphAppointmentId(graphTerminId));

        return speichern(neu, neuesFenster);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrphanedReservation> verwaisteFinden(Instant vor) {
        return repository.findByStateAndExpiresAtBefore(SlotStatus.PENDING, vor).stream()
                .map(zeile -> new OrphanedReservation(
                        zeile.getId(),
                        zeile.getBusinessId(),
                        zeile.getServiceId(),
                        zeile.getStaffMemberId(),
                        zeile.getStartUtc(),
                        zeile.getEndUtc()))
                .toList();
    }

    // ───────────────────────────── Hilfsmethoden ─────────────────────────────

    /**
     * CORE COLLISION ALGORITHM (Oracle Compatible)
     * 
     * Since Oracle Database does not support PostgreSQL's `EXCLUDE USING gist` constraint 
     * (as originally described in docs/PLAN-COLISION-RESERVAS.md), we prevent double 
     * bookings programmatically using a Pessimistic Locking strategy.
     * 
     * The algorithm works as follows:
     * 1. Lock: Acquire a database-level PESSIMISTIC_WRITE lock on the staff member's row. 
     *    This serializes all incoming booking requests for the same staff member, ensuring 
     *    that only one thread/transaction can process a booking for this worker at a time.
     * 2. Check: Query the database (`countOverlappingReservations`) to see if there are 
     *    any existing reservations (PENDING or CONFIRMED) that overlap with the requested 
     *    start and end time for this staff member.
     * 3. Act: If overlaps > 0, we immediately abort and throw an HTTP 409 (SlotConflictException).
     *    If overlaps == 0, we insert the new reservation and commit the transaction, releasing the lock.
     * 
     * {@code saveAllAndFlush} is essential here to ensure the transaction is written to the 
     * database before the lock is released.
     */
    private List<Long> speichern(List<SlotReservationEntity> zeilen, SlotRequest request) {
        // 1. Lock the staff members and check for overlaps BEFORE inserting
        for (String staffMemberId : request.mitarbeiterIds()) {
            // Pessimistic lock the staff member row to serialize requests for the same staff member
            staffRepository.lockByMsStaffMemberId(staffMemberId);
            
            // Check for overlaps
            int overlaps = repository.countOverlappingReservations(
                    request.businessId(),
                    staffMemberId,
                    request.start(),
                    request.ende()
            );
            
            if (overlaps > 0) {
                log.info("Slot bereits belegt: Betrieb={}, Mitarbeiter={}, {} – {}",
                        request.businessId(), request.mitarbeiterIds(), request.start(), request.ende());
                throw new SlotConflictException(
                        "Der Zeitraum %s bis %s ist für mindestens einen der Mitarbeiter %s bereits belegt."
                                .formatted(request.start(), request.ende(), request.mitarbeiterIds()));
            }
        }

        try {
            return repository.saveAllAndFlush(zeilen).stream()
                    .map(SlotReservationEntity::getId)
                    .toList();
        } catch (DataIntegrityViolationException ex) {
            log.info("DataIntegrityViolationException while saving slot: {}", ex.getMessage());
            throw new SlotConflictException("Datenbankfehler bei der Reservierung", ex);
        }
    }

    private void freigebenIntern(List<SlotReservationEntity> zeilen) {
        Instant jetzt = Instant.now();
        for (SlotReservationEntity zeile : zeilen) {
            zeile.setState(SlotStatus.RELEASED);
            zeile.setUpdatedAt(jetzt);
        }
        repository.saveAll(zeilen);
    }

    private SlotReservationEntity neueReservierung(SlotRequest request, String mitarbeiterId) {
        Instant jetzt = Instant.now();

        SlotReservationEntity zeile = new SlotReservationEntity();
        zeile.setBusinessId(request.businessId());
        zeile.setServiceId(request.serviceId());
        zeile.setStaffMemberId(mitarbeiterId);
        zeile.setStartUtc(request.start());
        zeile.setEndUtc(request.ende());
        zeile.setState(SlotStatus.PENDING);
        zeile.setExpiresAt(jetzt.plus(reservierungsTtl));
        zeile.setCreatedAt(jetzt);
        zeile.setUpdatedAt(jetzt);
        return zeile;
    }
}


