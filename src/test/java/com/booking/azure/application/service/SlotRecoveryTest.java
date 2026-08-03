package com.booking.azure.application.service;

import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.support.GraphApiMockTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifizierte Wiederherstellung verwaister Reservierungen
 * (docs/PLAN-COLISION-RESERVAS.md §2.5).
 *
 * <h2>Der Kern dieser Testreihe</h2>
 *
 * Der Unterschied zwischen einem korrekten und einem schädlichen
 * Wiederherstellungslauf liegt in einem einzigen Fall: eine abgelaufene
 * Reservierung, zu der in Graph <b>doch</b> ein Termin existiert. Wird sie
 * freigegeben, entsteht eine lautlose Doppelbuchung.
 *
 * Ein blinder Job besteht {@link #orphansWithoutAnAppointmentAreReleased}
 * problemlos – und fällt bei {@link #orphansWithAnAppointmentAreNotReleased}
 * durch. Deshalb ist der zweite Test der eigentliche Prüfstein.
 */
public class SlotRecoveryTest extends GraphApiMockTest {

    private static final String BETRIEB_ID = "agenturtest";
    private static final String DIENST_ID = "dienst-1";
    private static final String MITARBEITER_A = "mitarbeiter-a";
    private static final String TERMIN_ID = "TERMIN-1";

    private static final String GRAPH_TERMIN_PFAD =
            GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + BETRIEB_ID + "/appointments";
    private static final String GRAPH_KALENDER_PFAD =
            GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + BETRIEB_ID + "/calendarView";
    private static final String EIGENE_API = "/api/businesses/" + BETRIEB_ID + "/appointments";

    @Autowired
    private SlotRecoveryService recovery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Erzeugt eine orphaned Reservierung auf dem realistischen Weg: Graph
     * antwortet zu langsam, die Reservierung bleibt PENDING stehen.
     */
    @BeforeMethod
    public void createOrphanedReservation() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(appointmentJson())));

        ResponseEntity<String> response = buchen();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        // Frist vorziehen, statt 90 Sekunden zu warten.
        jdbcTemplate.execute("UPDATE slot_reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' MINUTE WHERE state = 'PENDING'");

        assertThat(anzahlMitZustand("PENDING"))
                .as("Vorbedingung: genau eine orphaned PENDING-Reservierung")
                .isEqualTo(1);
    }

    @Test(description = "Termin existiert in Graph → wiederherstellen, NICHT freigeben")
    public void orphansWithAnAppointmentAreNotReleased() {
        // Der POST hat Graph doch erreicht: der Termin steht im Kalender.
        graphKalenderLiefert("""
                {
                  "value": [{
                    "id": "%s",
                    "serviceId": "%s",
                    "staffMemberIds": ["%s"],
                    "startDateTime": { "dateTime": "2026-08-03T10:00:00", "timeZone": "UTC" },
                    "endDateTime":   { "dateTime": "2026-08-03T11:00:00", "timeZone": "UTC" }
                  }]
                }
                """.formatted(TERMIN_ID, DIENST_ID, MITARBEITER_A));

        int processed = recovery.recoverOrphaned();

        assertThat(processed).isEqualTo(1);
        assertThat(anzahlMitZustand("CONFIRMED"))
                .as("""
                        Der Termin existiert – der Schreibvorgang hat stattgefunden. Die \
                        Reservierung muss auf CONFIRMED gehoben werden, nicht freigegeben.""")
                .isEqualTo(1);
        assertThat(anzahlMitZustand("RELEASED")).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT graph_appointment_id FROM slot_reservation WHERE state = 'CONFIRMED'", String.class))
                .as("Die Reservierung muss mit dem gefundenen Termin verknüpft werden")
                .isEqualTo(TERMIN_ID);

        // Und der Slot muss weiterhin belegt sein.
        graphAcceptsAppointments();
        assertThat(buchen().getStatusCode())
                .as("""
                        Genau hier scheitert ein blind löschender Job: er gäbe den Slot frei, \
                        obwohl der Termin im Kalender des Mitarbeiters steht.""")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test(description = "Kein Termin in Graph → freigeben, Slot wieder buchbar")
    public void orphansWithoutAnAppointmentAreReleased() {
        graphKalenderLiefert("{ \"value\": [] }");

        int processed = recovery.recoverOrphaned();

        assertThat(processed).isEqualTo(1);
        assertThat(anzahlMitZustand("RELEASED")).isEqualTo(1);
        assertThat(anzahlMitZustand("PENDING")).isZero();

        graphAcceptsAppointments();
        assertThat(buchen().getStatusCode())
                .as("Der Slot muss wieder buchbar sein")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Graph nicht erreichbar → Reservierung unverändert lassen")
    public void ohneAntwortVonGraphWirdNichtsGeaendert() {
        GRAPH_MOCK.stubFor(get(urlPathEqualTo(GRAPH_KALENDER_PFAD))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"nicht verfügbar\"}")));

        int processed = recovery.recoverOrphaned();

        assertThat(processed)
                .as("Ohne Antwort von Graph gibt es keine Entscheidungsgrundlage")
                .isZero();
        assertThat(anzahlMitZustand("PENDING"))
                .as("""
                        Die Zeile bleibt PENDING und der Slot blockiert. Das ist die sichere \
                        Richtung: ein blockierter Slot ist ein Ärgernis, eine falsch \
                        freigegebene Reservierung eine Doppelbuchung.""")
                .isEqualTo(1);
    }

    @Test(description = "Termin eines anderen Mitarbeiters zählt nicht als Treffer")
    public void differentStaffMemberIsNotAMatch() {
        graphKalenderLiefert("""
                {
                  "value": [{
                    "id": "FREMDER-TERMIN",
                    "serviceId": "%s",
                    "staffMemberIds": ["mitarbeiter-fremd"],
                    "startDateTime": { "dateTime": "2026-08-03T10:00:00", "timeZone": "UTC" },
                    "endDateTime":   { "dateTime": "2026-08-03T11:00:00", "timeZone": "UTC" }
                  }]
                }
                """.formatted(DIENST_ID));

        recovery.recoverOrphaned();

        assertThat(anzahlMitZustand("RELEASED"))
                .as("Ein überlappender Termin eines anderen Mitarbeiters gehört nicht zu dieser Reservierung")
                .isEqualTo(1);
    }

    // ─────────────────────────── Hilfsmethoden ───────────────────────────

    private void graphKalenderLiefert(String json) {
        GRAPH_MOCK.stubFor(get(urlPathEqualTo(GRAPH_KALENDER_PFAD))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)));
    }

    private void graphAcceptsAppointments() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(appointmentJson())));
    }

    private Integer anzahlMitZustand(String state) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM slot_reservation WHERE state = ?", Integer.class, state);
    }

    private static String appointmentJson() {
        return """
                {
                  "id": "%s",
                  "serviceId": "%s",
                  "staffMemberIds": ["%s"],
                  "startDateTime": { "dateTime": "2026-08-03T10:00:00", "timeZone": "UTC" },
                  "endDateTime":   { "dateTime": "2026-08-03T11:00:00", "timeZone": "UTC" }
                }
                """.formatted(TERMIN_ID, DIENST_ID, MITARBEITER_A);
    }

    private ResponseEntity<String> buchen() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setServiceId(DIENST_ID);
        request.setWorkerNames(List.of(MITARBEITER_A));
        request.setStartDateTime(zeit("2026-08-03T10:00:00"));
        request.setEndDateTime(zeit("2026-08-03T11:00:00"));

        BookingCustomerInfoDto kunde = new BookingCustomerInfoDto();
        kunde.setName("Testkunde");
        kunde.setEmailAddress("kunde@example.de");
        request.setCustomers(List.of(kunde));

        return restTemplate.exchange(EIGENE_API, HttpMethod.POST, new HttpEntity<>(request, authHeaders), String.class);
    }

    private DateTimeTimeZoneDto zeit(String zeitstempel) {
        DateTimeTimeZoneDto dto = new DateTimeTimeZoneDto();
        dto.setDateTime(zeitstempel);
        dto.setTimeZone("UTC");
        return dto;
    }
}
