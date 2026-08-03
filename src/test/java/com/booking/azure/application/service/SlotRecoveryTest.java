package com.booking.azure.application.service;

import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.booking.azure.domain.command.CreateAppointmentRequest;
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
 * Ein blinder Job besteht {@link #verwaisteOhneTerminWerdenFreigegeben}
 * problemlos – und fällt bei {@link #verwaisteMitTerminWerdenNichtFreigegeben}
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
     * Erzeugt eine verwaiste Reservierung auf dem realistischen Weg: Graph
     * antwortet zu langsam, die Reservierung bleibt PENDING stehen.
     */
    @BeforeMethod
    public void verwaisteReservierungErzeugen() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(terminJson())));

        ResponseEntity<String> antwort = buchen();
        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        // Frist vorziehen, statt 90 Sekunden zu warten.
        jdbcTemplate.execute("UPDATE slot_reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' MINUTE WHERE state = 'PENDING'");

        assertThat(anzahlMitZustand("PENDING"))
                .as("Vorbedingung: genau eine verwaiste PENDING-Reservierung")
                .isEqualTo(1);
    }

    @Test(description = "Termin existiert in Graph → wiederherstellen, NICHT freigeben")
    public void verwaisteMitTerminWerdenNichtFreigegeben() {
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

        int bearbeitet = recovery.recoverOrphaned();

        assertThat(bearbeitet).isEqualTo(1);
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
        graphNimmtTermineAn();
        assertThat(buchen().getStatusCode())
                .as("""
                        Genau hier scheitert ein blind löschender Job: er gäbe den Slot frei, \
                        obwohl der Termin im Kalender des Mitarbeiters steht.""")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test(description = "Kein Termin in Graph → freigeben, Slot wieder buchbar")
    public void verwaisteOhneTerminWerdenFreigegeben() {
        graphKalenderLiefert("{ \"value\": [] }");

        int bearbeitet = recovery.recoverOrphaned();

        assertThat(bearbeitet).isEqualTo(1);
        assertThat(anzahlMitZustand("RELEASED")).isEqualTo(1);
        assertThat(anzahlMitZustand("PENDING")).isZero();

        graphNimmtTermineAn();
        assertThat(buchen().getStatusCode())
                .as("Der Slot muss wieder buchbar sein")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Graph nicht erreichbar → Reservierung unverändert lassen")
    public void ohneAntwortVonGraphWirdNichtsGeaendert() {
        GRAPH_MOCK.stubFor(get(urlPathEqualTo(GRAPH_KALENDER_PFAD))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"nicht verfügbar\"}")));

        int bearbeitet = recovery.recoverOrphaned();

        assertThat(bearbeitet)
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
    public void andererMitarbeiterIstKeinTreffer() {
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

    private void graphNimmtTermineAn() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(terminJson())));
    }

    private Integer anzahlMitZustand(String zustand) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM slot_reservation WHERE state = ?", Integer.class, zustand);
    }

    private static String terminJson() {
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
        CreateAppointmentRequest anfrage = new CreateAppointmentRequest();
        anfrage.setServiceId(DIENST_ID);
        anfrage.setWorkerNames(List.of(MITARBEITER_A));
        anfrage.setStartDateTime(zeit("2026-08-03T10:00:00"));
        anfrage.setEndDateTime(zeit("2026-08-03T11:00:00"));

        BookingCustomerInfoDto kunde = new BookingCustomerInfoDto();
        kunde.setName("Testkunde");
        kunde.setEmailAddress("kunde@example.de");
        anfrage.setCustomers(List.of(kunde));

        return restTemplate.exchange(EIGENE_API, HttpMethod.POST, new HttpEntity<>(anfrage, authHeaders), String.class);
    }

    private DateTimeTimeZoneDto zeit(String zeitstempel) {
        DateTimeTimeZoneDto dto = new DateTimeTimeZoneDto();
        dto.setDateTime(zeitstempel);
        dto.setTimeZone("UTC");
        return dto;
    }
}
