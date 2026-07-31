package com.booking.azure.application.service;

import com.booking.azure.domain.model.OrphanedReservation;
import com.booking.azure.domain.port.in.AppointmentManagement;
import com.booking.azure.domain.port.out.SlotReservationPort;
import com.booking.azure.dto.BookingAppointmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wiederherstellung verwaister {@code PENDING}-Reservierungen.
 *
 * <h2>Warum es diesen Dienst geben muss</h2>
 *
 * Seit die Kompensation zwischen „Graph hat abgelehnt" und „Graph hat nicht
 * geantwortet" unterscheidet, bleibt eine Reservierung bei jeder
 * Zeitüberschreitung {@code PENDING} stehen. Das ist richtig – es verhindert
 * die Doppelbuchung – aber ohne diesen Dienst bliebe der Slot <b>dauerhaft</b>
 * blockiert.
 *
 * <h2>Die eine Regel, die zählt</h2>
 *
 * <b>Erst bei Graph nachsehen, dann entscheiden.</b> Ein Job, der abgelaufene
 * Zeilen nach Zeitplan löscht, verursacht genau den Fehler, den er beheben soll:
 *
 * <pre>
 *   t=0    A reserviert, expires_at = t+90. POST an Graph abgesetzt.
 *   t=0,1  Graph ist überlastet und braucht 120 s.
 *   t=90   Ein blinder Job gibt die "abgelaufene" Zeile frei. Slot frei.
 *   t=91   B belegt den Slot, POST → 201
 *   t=120  Der POST von A erreicht Graph doch noch → 201
 *          Zwei überlappende Termine. Lautlos.
 * </pre>
 *
 * Das Ablaufen einer Zeile bricht keinen laufenden HTTP-Aufruf ab. Deshalb
 * fragt dieser Dienst für jede verwaiste Zeile {@code GET /calendarView} ab und
 * entscheidet erst danach:
 *
 * <ul>
 *   <li>Termin gefunden → {@code CONFIRMED}. <b>Wiederherstellen, nicht
 *       release</b> – der Schreibvorgang hat stattgefunden.</li>
 *   <li>Termin nicht gefunden → {@code RELEASED}. Der Slot wird wieder buchbar.</li>
 *   <li>Graph nicht erreichbar → nichts tun, im nächsten Lauf erneut versuchen.</li>
 * </ul>
 *
 * Siehe docs/PLAN-COLISION-RESERVAS.md §2.5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotRecoveryService {

    /**
     * Puffer um das Slot-Fenster beim Abfragen der Kalenderansicht.
     *
     * Microsoft Bookings kann Vor- und Nachbereitungszeiten auf einen Termin
     * aufschlagen, sodass die zurückgelieferten Zeiten nicht exakt den
     * angefragten entsprechen.
     */
    private static final Duration SUCHPUFFER = Duration.ofHours(1);

    private final SlotReservationPort slotReservation;
    private final AppointmentManagement appointmentManagement;

    /**
     * Periodischer Einstiegspunkt. Der eigentliche Ablauf steht in
     * {@link #verwaisteAufraeumen()}, damit er in Tests direkt aufrufbar ist.
     */
    @Scheduled(cron = "${buchung.slot-reservierung.wiederherstellung-cron:0 */5 * * * *}")
    public void geplanterLauf() {
        verwaisteAufraeumen();
    }

    /**
     * Prüft alle abgelaufenen {@code PENDING}-Reservierungen gegen Graph und
     * bestätigt oder gibt sie frei.
     *
     * @return Anzahl der bearbeiteten Reservierungen
     */
    public int verwaisteAufraeumen() {
        List<OrphanedReservation> verwaiste = slotReservation.verwaisteFinden(Instant.now());
        if (verwaiste.isEmpty()) {
            return 0;
        }

        log.info("{} verwaiste Slot-Reservierung(en) werden gegen Graph geprüft", verwaiste.size());

        // Nach Betrieb gruppieren: eine Kalenderabfrage je Agentur statt je Zeile.
        // Bei mehreren Mitarbeitern pro Termin entstehen mehrere Zeilen für
        // denselben Zeitraum – die würden sonst dieselbe Abfrage mehrfach auslösen.
        Map<String, List<OrphanedReservation>> nachBetrieb = verwaiste.stream()
                .collect(Collectors.groupingBy(OrphanedReservation::businessId));

        int bearbeitet = 0;
        for (Map.Entry<String, List<OrphanedReservation>> eintrag : nachBetrieb.entrySet()) {
            bearbeitet += betriebPruefen(eintrag.getKey(), eintrag.getValue());
        }
        return bearbeitet;
    }

    private int betriebPruefen(String businessId, List<OrphanedReservation> verwaiste) {
        Instant von = verwaiste.stream().map(OrphanedReservation::start)
                .min(Instant::compareTo).orElseThrow().minus(SUCHPUFFER);
        Instant bis = verwaiste.stream().map(OrphanedReservation::ende)
                .max(Instant::compareTo).orElseThrow().plus(SUCHPUFFER);

        List<BookingAppointmentDto> termine;
        try {
            termine = appointmentManagement.kalenderAnsichtAbrufen(businessId, von.toString(), bis.toString());
        } catch (RuntimeException ex) {
            // Ohne Antwort von Graph gibt es keine Grundlage für eine Entscheidung.
            // Nichts anfassen und im nächsten Lauf erneut versuchen – die Zeilen
            // bleiben PENDING, der Slot bleibt sicherheitshalber blockiert.
            log.warn("Kalenderansicht für Betrieb {} nicht abrufbar, {} Reservierung(en) bleiben "
                    + "unverändert: {}", businessId, verwaiste.size(), ex.getMessage());
            return 0;
        }

        int bearbeitet = 0;
        for (OrphanedReservation zeile : verwaiste) {
            Optional<BookingAppointmentDto> treffer = passendenTerminSuchen(zeile, termine);

            if (treffer.isPresent()) {
                // Der Schreibvorgang hat stattgefunden. Wiederherstellen, NICHT release.
                slotReservation.confirm(List.of(zeile.id()), treffer.get().getId());
                log.info("Verwaiste Reservierung {} wiederhergestellt: Termin {} existiert in Graph",
                        zeile.id(), treffer.get().getId());
            } else {
                slotReservation.release(List.of(zeile.id()));
                log.info("Verwaiste Reservierung {} freigegeben: kein passender Termin in Graph "
                        + "(Mitarbeiter {}, {} – {})",
                        zeile.id(), zeile.mitarbeiterId(), zeile.start(), zeile.ende());
            }
            bearbeitet++;
        }
        return bearbeitet;
    }

    /**
     * Sucht einen Termin, der zu der verwaisten Reservierung passt.
     *
     * Kriterien: derselbe Mitarbeiter und ein überlappender Zeitraum.
     *
     * <p>Bewusst Überlappung statt exakter Gleichheit: Bookings kann Puffer
     * aufschlagen, und Graph liefert Zeiten je nach Endpunkt in
     * unterschiedlichen Zonen zurück. Ein zu strenger Vergleich würde den
     * Termin verfehlen und den Slot fälschlich release – der teurere Fehler.
     */
    private Optional<BookingAppointmentDto> passendenTerminSuchen(
            OrphanedReservation zeile, List<BookingAppointmentDto> termine) {

        return termine.stream()
                .filter(termin -> termin.getStaffMemberIds() != null
                        && termin.getStaffMemberIds().contains(zeile.mitarbeiterId()))
                .filter(termin -> ueberlappt(zeile, termin))
                .findFirst();
    }

    private boolean ueberlappt(OrphanedReservation zeile, BookingAppointmentDto termin) {
        try {
            Instant terminStart = TimeZoneConverter.toInstant(termin.getStartDateTime());
            Instant terminEnde = TimeZoneConverter.toInstant(termin.getEndDateTime());
            // Halboffene Intervalle, wie in der EXCLUDE-Bedingung.
            return terminStart.isBefore(zeile.ende()) && zeile.start().isBefore(terminEnde);
        } catch (IllegalArgumentException ex) {
            log.warn("Termin {} hat unlesbare Zeitangaben und wird beim Abgleich übersprungen: {}",
                    termin.getId(), ex.getMessage());
            return false;
        }
    }
}


