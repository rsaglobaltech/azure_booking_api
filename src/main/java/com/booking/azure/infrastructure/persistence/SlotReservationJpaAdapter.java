package com.booking.azure.infrastructure.persistence;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.SlotAnfrage;
import com.booking.azure.domain.model.SlotStatus;
import com.booking.azure.domain.model.VerwaisteReservierung;
import com.booking.azure.domain.port.out.SlotReservierung;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Infrastruktur-Adapter: implementiert {@link SlotReservierung} über PostgreSQL.
 *
 * <h2>Wo die eigentliche Entscheidung fällt</h2>
 *
 * Nicht in diesem Java-Code, sondern in der Datenbankbedingung
 * {@code ex_slot_overlap}. Sie ist per Definition atomar: von mehreren
 * gleichzeitigen {@code INSERT}s auf dasselbe Zeitfenster kann genau einer
 * erfolgreich sein, unabhängig davon, wie viele Anwendungsinstanzen laufen.
 *
 * Java prüft hier nichts nach – jede Vorabprüfung im Code wäre wieder ein
 * Time-of-Check-to-Time-of-Use-Fenster.
 */
@Slf4j
@Component
public class SlotReservationJpaAdapter implements SlotReservierung {

    private static final List<SlotStatus> BLOCKIEREND =
            List.of(SlotStatus.PENDING, SlotStatus.CONFIRMED);

    private final SlotReservationRepository repository;
    private final Duration reservierungsTtl;

    public SlotReservationJpaAdapter(
            SlotReservationRepository repository,
            @Value("${buchung.slot-reservierung.ttl:PT90S}") Duration reservierungsTtl) {
        this.repository = repository;
        this.reservierungsTtl = reservierungsTtl;
    }

    @Override
    @Transactional
    public List<Long> reservieren(SlotAnfrage anfrage) {
        List<SlotReservationEntity> neu = anfrage.mitarbeiterIds().stream()
                .map(mitarbeiterId -> neueReservierung(anfrage, mitarbeiterId))
                .toList();

        return speichern(neu, anfrage);
    }

    @Override
    @Transactional
    public void bestaetigen(List<Long> reservierungsIds, String graphTerminId) {
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
    public void freigeben(List<Long> reservierungsIds) {
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
    public List<Long> umbuchen(String graphTerminId, SlotAnfrage neuesFenster) {
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
    public List<VerwaisteReservierung> verwaisteFinden(Instant vor) {
        return repository.findByStateAndExpiresAtBefore(SlotStatus.PENDING, vor).stream()
                .map(zeile -> new VerwaisteReservierung(
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
     * Schreibt die Zeilen und übersetzt einen Verstoß gegen die
     * EXCLUDE-Bedingung in eine fachliche Ausnahme.
     *
     * {@code saveAllAndFlush} ist wesentlich: ohne das Flush schlüge die
     * Bedingung erst beim Festschreiben der Transaktion zu – also außerhalb
     * dieses {@code try}-Blocks – und die Ausnahme entkäme als technischer
     * Fehler statt als HTTP 409.
     */
    private List<Long> speichern(List<SlotReservationEntity> zeilen, SlotAnfrage anfrage) {
        try {
            return repository.saveAllAndFlush(zeilen).stream()
                    .map(SlotReservationEntity::getId)
                    .toList();
        } catch (DataIntegrityViolationException ex) {
            log.info("Slot bereits belegt: Betrieb={}, Mitarbeiter={}, {} – {}",
                    anfrage.betriebId(), anfrage.mitarbeiterIds(), anfrage.start(), anfrage.ende());
            throw new SlotConflictException(
                    "Der Zeitraum %s bis %s ist für mindestens einen der Mitarbeiter %s bereits belegt."
                            .formatted(anfrage.start(), anfrage.ende(), anfrage.mitarbeiterIds()), ex);
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

    private SlotReservationEntity neueReservierung(SlotAnfrage anfrage, String mitarbeiterId) {
        Instant jetzt = Instant.now();

        SlotReservationEntity zeile = new SlotReservationEntity();
        zeile.setBusinessId(anfrage.betriebId());
        zeile.setServiceId(anfrage.dienstId());
        zeile.setStaffMemberId(mitarbeiterId);
        zeile.setStartUtc(anfrage.start());
        zeile.setEndUtc(anfrage.ende());
        zeile.setState(SlotStatus.PENDING);
        zeile.setExpiresAt(jetzt.plus(reservierungsTtl));
        zeile.setCreatedAt(jetzt);
        zeile.setUpdatedAt(jetzt);
        return zeile;
    }
}
