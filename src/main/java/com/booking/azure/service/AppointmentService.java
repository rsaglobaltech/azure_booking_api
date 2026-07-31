package com.booking.azure.service;

import com.booking.azure.domain.exception.GraphAntwortException;
import com.booking.azure.domain.model.SlotAnfrage;
import com.booking.azure.domain.port.in.TerminVerwaltung;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.booking.azure.domain.port.out.SlotReservierung;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.GraphListResponse;
import com.booking.azure.dto.request.CreateAppointmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Anwendungsdienst für die Terminverwaltung (Appointments).
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link TerminVerwaltung}.
 *   Abhängig vom ausgehenden Port {@link GraphApiAnfrage}; die konkrete
 *   Infrastruktur (GraphApiClient) ist dieser Schicht unbekannt.
 *
 * Alle Terme beziehen sich auf einen einzelnen Buchungsbetrieb (Agentur)
 * innerhalb desselben Azure-AD-Mandanten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService implements TerminVerwaltung {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiAnfrage graphApiAnfrage;

    /**
     * Ausgehender Port für die atomare Slot-Reservierung.
     *
     * Ohne ihn wäre die Terminerstellung eine reine Durchreichung an Graph –
     * und Graph erlaubt am administrativen Endpunkt Überbuchung bewusst.
     */
    private final SlotReservierung slotReservierung;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ──────────────────────────────── Hilfsmethoden ────────────────────────────

    private String terminePfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/appointments";
    }

    private String kalenderPfad(String betriebId) {
        return "/solutions/bookingBusinesses/" + betriebId + "/calendarView";
    }

    // ──────────────────────────── Use-Case-Implementierungen ────────────────────

    /**
     * Alle Termine eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID der Agentur (z. B. agentur@midominio.com)
     * @return Liste aller Termine
     */
    @Override
    public List<BookingAppointmentDto> termineAuflisten(String betriebId) {
        log.info("Termine werden aufgelistet für Betrieb: {}", betriebId);
        GraphListResponse<BookingAppointmentDto> antwort = graphApiAnfrage.get(
                terminePfad(betriebId), GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    /**
     * Termine in einem Datumszeitraum abrufen (Kalenderansicht).
     * Datumsformat ISO 8601, z. B. {@code 2024-01-01T00:00:00Z}
     *
     * @param betriebId      ID der Agentur
     * @param startDatumZeit Startdatum und -zeit
     * @param endDatumZeit   Enddatum und -zeit
     * @return Liste der Termine im Zeitraum
     */
    @Override
    public List<BookingAppointmentDto> kalenderAnsichtAbrufen(String betriebId,
                                                              String startDatumZeit,
                                                              String endDatumZeit) {
        log.info("Kalenderansicht für Betrieb {}: {} → {}", betriebId, startDatumZeit, endDatumZeit);
        String pfad = kalenderPfad(betriebId)
                + "?startDateTime=" + startDatumZeit
                + "&endDateTime=" + endDatumZeit;
        GraphListResponse<BookingAppointmentDto> antwort = graphApiAnfrage.get(pfad, GraphListResponse.class);
        return listeMappen(antwort.getValue(), BookingAppointmentDto.class);
    }

    /**
     * Einen einzelnen Termin anhand seiner ID abrufen.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID (GUID) des Termins
     * @return Termindaten
     */
    @Override
    public BookingAppointmentDto terminAbrufen(String betriebId, String terminId) {
        log.info("Termin {} wird abgerufen für Betrieb {}", terminId, betriebId);
        return graphApiAnfrage.get(terminePfad(betriebId) + "/" + terminId,
                BookingAppointmentDto.class);
    }

    /**
     * Neuen Termin erstellen und Mitarbeiter sowie Kunden zuweisen.
     *
     * <h2>Ablauf</h2>
     * <pre>
     *   1. Slot reservieren        → eigene Transaktion, sofort festgeschrieben
     *   2. POST an Graph           → außerhalb jeder Transaktion
     *   3a. Reservierung bestätigen (Erfolg)
     *   3b. Reservierung freigeben  (Fehler → Kompensation)
     * </pre>
     *
     * Das Festschreiben in Schritt 1 geschieht bewusst <b>vor</b> dem Netzaufruf:
     * eine über einen HTTP-Aufruf offen gehaltene Transaktion erschöpft unter
     * Last den Verbindungspool.
     *
     * @param betriebId ID der Agentur
     * @param anfrage   Termindaten (Dienst, Zeitraum, Mitarbeiter, Kunde)
     * @return Der erstellte Termin
     * @throws com.booking.azure.domain.exception.SlotConflictException
     *         wenn der Zeitraum für einen der Mitarbeiter bereits belegt ist
     */
    @Override
    public BookingAppointmentDto terminErstellen(String betriebId, CreateAppointmentRequest anfrage) {
        log.info("Neuer Termin wird erstellt in Betrieb {}, Dienst: {}", betriebId, anfrage.getServiceId());

        if (ohneMitarbeiter(anfrage)) {
            // Ohne Mitarbeiterzuordnung gibt es keinen Kalender, der kollidieren
            // könnte. Der Termin wird durchgereicht, aber protokolliert – falls
            // das fachlich unerwünscht ist, gehört hier eine Ablehnung hin.
            log.warn("Termin ohne Mitarbeiterzuordnung in Betrieb {} – keine Slot-Reservierung möglich",
                    betriebId);
            return graphApiAnfrage.post(terminePfad(betriebId), anfrage, BookingAppointmentDto.class);
        }

        List<Long> reservierungen = slotReservierung.reservieren(slotAnfrage(betriebId, anfrage));

        // Ab hier hält diese Anfrage den Slot exklusiv – für alle Instanzen sichtbar.
        try {
            BookingAppointmentDto termin = graphApiAnfrage.post(
                    terminePfad(betriebId), anfrage, BookingAppointmentDto.class);
            slotReservierung.bestaetigen(reservierungen, termin.getId());
            return termin;

        } catch (GraphAntwortException ex) {
            // Graph hat geantwortet und abgelehnt. Gewissheit: kein Termin angelegt.
            // Freigabe ist sicher.
            log.error("Graph lehnte den Termin ab (Status {}), Reservierung {} wird freigegeben: {}",
                    ex.getStatus(), reservierungen, ex.getMessage());
            slotReservierung.freigeben(reservierungen);
            throw ex;

        } catch (RuntimeException ex) {
            // Keine Antwort erhalten (Zeitüberschreitung, Verbindungsabbruch) oder
            // unerwarteter Fehler. Ob Graph den Termin angelegt hat, ist unbekannt.
            //
            // Die Reservierung bleibt daher PENDING. Sie hier freizugeben würde die
            // Doppelbuchung erzeugen, die sie verhindern soll: die Wiederholung des
            // Clients bekäme den Slot, während der erste POST Graph doch noch erreicht.
            //
            // Auflösung übernimmt der Wiederherstellungsjob, der über
            // GET /calendarView prüft, ob der Termin existiert.
            log.error("Kein definitives Ergebnis von Graph. Reservierung {} bleibt PENDING und wird "
                            + "NICHT freigegeben – Prüfung durch den Wiederherstellungsjob erforderlich. {}",
                    reservierungen, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Bestehenden Termin aktualisieren (Umbuchung).
     *
     * Alter Slot wird freigegeben und der neue belegt – in einer Transaktion,
     * damit zwischen beidem keine andere Anfrage den alten Slot übernimmt.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID des Termins
     * @param anfrage   Neue Termindaten
     * @return Der aktualisierte Termin
     * @throws com.booking.azure.domain.exception.SlotConflictException
     *         wenn das neue Zeitfenster bereits belegt ist
     */
    @Override
    public BookingAppointmentDto terminAktualisieren(String betriebId,
                                                     String terminId,
                                                     CreateAppointmentRequest anfrage) {
        log.info("Termin {} wird aktualisiert in Betrieb {}", terminId, betriebId);

        if (ohneMitarbeiter(anfrage)) {
            log.warn("Umbuchung ohne Mitarbeiterzuordnung für Termin {} – keine Slot-Prüfung", terminId);
            return graphApiAnfrage.patch(terminePfad(betriebId) + "/" + terminId,
                    anfrage, BookingAppointmentDto.class);
        }

        List<Long> neueReservierungen =
                slotReservierung.umbuchen(terminId, slotAnfrage(betriebId, anfrage));

        try {
            BookingAppointmentDto termin = graphApiAnfrage.patch(
                    terminePfad(betriebId) + "/" + terminId, anfrage, BookingAppointmentDto.class);
            slotReservierung.bestaetigen(neueReservierungen, terminId);
            return termin;

        } catch (GraphAntwortException ex) {
            // Vollständige Kompensation ist hier nicht möglich: der alte Slot ist
            // bereits freigegeben und könnte inzwischen vergeben sein. Der Termin
            // steht in Graph weiterhin zur alten Zeit, ohne dass wir dessen Slot
            // halten. Diese Abweichung findet der Abgleichsjob (Phase 4).
            log.error("Graph lehnte die Umbuchung von Termin {} ab (Status {}). Neue Reservierung {} "
                            + "wird freigegeben. ACHTUNG: alter Slot bleibt freigegeben – Abgleich erforderlich.",
                    terminId, ex.getStatus(), neueReservierungen);
            slotReservierung.freigeben(neueReservierungen);
            throw ex;

        } catch (RuntimeException ex) {
            // Unbekannter Ausgang: die Umbuchung kann in Graph stattgefunden haben.
            // Die neue Reservierung bleibt PENDING – siehe Begründung in terminErstellen.
            log.error("Kein definitives Ergebnis bei der Umbuchung von Termin {}. Reservierung {} "
                            + "bleibt PENDING. Abgleich erforderlich. {}",
                    terminId, neueReservierungen, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Termin stornieren / löschen.
     *
     * Der Slot wird erst nach erfolgreichem Löschen in Graph freigegeben.
     * Scheitert das Löschen, bleibt der Slot belegt – das ist die sichere
     * Richtung: lieber ein blockierter Slot als eine Doppelbuchung.
     *
     * @param betriebId ID der Agentur
     * @param terminId  ID des zu stornierenden Termins
     */
    @Override
    public void terminStornieren(String betriebId, String terminId) {
        log.info("Termin {} wird storniert in Betrieb {}", terminId, betriebId);

        graphApiAnfrage.delete(terminePfad(betriebId) + "/" + terminId);

        int freigegeben = slotReservierung.freigebenNachTerminId(terminId);
        log.debug("{} Slot-Reservierung(en) nach Stornierung von Termin {} freigegeben",
                freigegeben, terminId);
    }

    // ─────────────────────────────── Hilfsmethoden ──────────────────────────────

    private boolean ohneMitarbeiter(CreateAppointmentRequest anfrage) {
        return anfrage.getStaffMemberIds() == null || anfrage.getStaffMemberIds().isEmpty();
    }

    /**
     * Baut die Reservierungsanfrage und normalisiert dabei nach UTC.
     *
     * Die Umrechnung ist nicht optional: {@code 10:00 Europe/Berlin} und
     * {@code 08:00 UTC} sind derselbe Zeitpunkt und müssen als derselbe Slot
     * erkannt werden.
     */
    private SlotAnfrage slotAnfrage(String betriebId, CreateAppointmentRequest anfrage) {
        Instant start = ZeitzonenUmrechnung.zuInstant(anfrage.getStartDateTime());
        Instant ende = ZeitzonenUmrechnung.zuInstant(anfrage.getEndDateTime());

        return new SlotAnfrage(
                betriebId,
                anfrage.getServiceId(),
                anfrage.getStaffMemberIds(),
                start,
                ende);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}
