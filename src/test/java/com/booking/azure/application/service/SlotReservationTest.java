package com.booking.azure.application.service;

import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.support.GraphApiMockTest;
import org.testng.annotations.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abnahmekriterien der Slot-Reservierung
 * (docs/PLAN-COLISION-RESERVAS.md §9).
 *
 * Ergänzt {@link AppointmentConcurrencyTest} um die Fälle, die nicht über
 * Gleichzeitigkeit laufen: Zeitzonen, Kompensation, Stornierung, Umbuchung
 * und die Grenzen des Überschneidungsbegriffs.
 */
public class SlotReservationTest extends GraphApiMockTest {

    private static final String BETRIEB_ID = "agenturtest";
    private static final String DIENST_ID = "dienst-1";
    private static final String MITARBEITER_A = "mitarbeiter-a";
    private static final String MITARBEITER_B = "mitarbeiter-b";
    private static final String TERMIN_ID = "TERMIN-1";

    private static final String GRAPH_TERMIN_PFAD =
            GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + BETRIEB_ID + "/appointments";
    private static final String EIGENE_API = "/api/businesses/" + BETRIEB_ID + "/appointments";

    // ─────────────────────────── Zeitzonen ───────────────────────────

    @Test(description = "10:00 Europe/Berlin und 08:00 UTC sind derselbe Slot")
    public void zeitzonenWerdenNormalisiert() {
        graphAcceptsAppointments();

        ResponseEntity<String> berlin = buchen(request(
                zeit("2026-08-03T10:00:00", "Europe/Berlin"),
                zeit("2026-08-03T11:00:00", "Europe/Berlin"),
                MITARBEITER_A));

        ResponseEntity<String> utc = buchen(request(
                zeit("2026-08-03T08:00:00", "UTC"),
                zeit("2026-08-03T09:00:00", "UTC"),
                MITARBEITER_A));

        assertThat(berlin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(utc.getStatusCode())
                .as("""
                        Im August gilt in Berlin UTC+2. 10:00 Europe/Berlin und 08:00 UTC sind \
                        derselbe Zeitpunkt. Ohne Normalisierung nach UTC vor dem \
                        Datenbankzugriff bliebe diese Kollision unerkannt.""")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test(description = "Unbekannte Zeitzone wird mit HTTP 400 abgewiesen, nicht mit 502")
    public void unknownTimeZone() {
        graphAcceptsAppointments();

        ResponseEntity<String> response = buchen(request(
                zeit("2026-08-03T10:00:00", "W. Europe Standard Time"),
                zeit("2026-08-03T11:00:00", "W. Europe Standard Time"),
                MITARBEITER_A));

        assertThat(response.getStatusCode())
                .as("Windows-Zonennamen werden bewusst nicht übersetzt – klarer Eingabefehler")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────── Grenzen der Überschneidung ───────────────────────────

    @Test(description = "Direkt anschließende Termine (10:00–11:00, 11:00–12:00) kollidieren nicht")
    public void backToBackAppointmentsAreAllowed() {
        graphAcceptsAppointments();

        ResponseEntity<String> erster = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        ResponseEntity<String> zweiter = buchen(request(
                zeit("2026-08-03T11:00:00", "UTC"), zeit("2026-08-03T12:00:00", "UTC"), MITARBEITER_A));

        assertThat(erster.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(zweiter.getStatusCode())
                .as("""
                        Das Intervall ist halboffen '[)': die Endzeit gehört nicht mehr dazu. \
                        Ein Folgetermin um 11:00 ist zulässig. Andernfalls wäre der Kalender \
                        nach jedem Termin für eine Sekunde blockiert.""")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Gleicher Zeitraum, anderer Mitarbeiter – kein Konflikt")
    public void differentStaffMemberDoesNotConflict() {
        graphAcceptsAppointments();

        ResponseEntity<String> a = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        ResponseEntity<String> b = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_B));

        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Termin mit zwei Mitarbeitern scheitert, wenn einer davon belegt ist")
    public void oneBusyStaffMemberBlocksTheWholeRequest() {
        graphAcceptsAppointments();

        buchen(request(zeit("2026-08-03T10:00:00", "UTC"),
                zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_B));

        ResponseEntity<String> zuZweit = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"),
                MITARBEITER_A, MITARBEITER_B));

        assertThat(zuZweit.getStatusCode())
                .as("Reservierung ist ganz oder gar nicht – B ist belegt, also scheitert die Anfrage")
                .isEqualTo(HttpStatus.CONFLICT);

        // Und A darf danach nicht halb reserviert zurückbleiben.
        ResponseEntity<String> nurA = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        assertThat(nurA.getStatusCode())
                .as("Die gescheiterte Reservierung muss vollständig zurückgerollt sein")
                .isEqualTo(HttpStatus.CREATED);
    }

    // ─────────────────────────── Kompensation ───────────────────────────

    @Test(description = "Fehler von Graph gibt den Slot wieder frei")
    public void graphFailureReleasesTheSlot() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"kaputt\"}")));

        ResponseEntity<String> gescheitert = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        assertThat(gescheitert.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        graphAcceptsAppointments();
        ResponseEntity<String> erneut = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        assertThat(erneut.getStatusCode())
                .as("""
                        Nach dem Fehlschlag in Graph muss die Reservierung freigegeben worden sein, \
                        sonst bliebe der Slot dauerhaft blockiert obwohl kein Termin existiert.""")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Zeitüberschreitung gibt den Slot NICHT frei – sonst entsteht die Doppelbuchung")
    public void zeitueberschreitungGibtSlotNichtFrei() {
        // Graph antwortet langsamer als das Zeitlimit (2 s im Testprofil).
        // Wichtig: die Anfrage erreicht Graph trotzdem – der Termin kann angelegt werden.
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(appointmentJson())));

        ResponseEntity<String> erste = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        assertThat(erste.getStatusCode())
                .as("Kein definitives Ergebnis von Graph → 504, nicht 502")
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        assertThat(GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_TERMIN_PFAD))).size())
                .as("Die Anfrage hat Graph erreicht – der Termin kann sehr wohl existieren")
                .isEqualTo(1);

        // Der Client wiederholt, wie es HTTP-Clients nach einer Zeitüberschreitung tun.
        // Graph antwortet jetzt sofort.
        graphAcceptsAppointments();
        ResponseEntity<String> wiederholung = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        assertThat(wiederholung.getStatusCode())
                .as("""
                        Der Slot muss weiterhin belegt sein. Eine Freigabe bei Zeitüberschreitung \
                        wäre eine Freigabe ohne Gewissheit: die Wiederholung bekäme den Slot, \
                        während der erste POST Graph doch noch erreicht – zwei überlappende Termine.""")
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_TERMIN_PFAD))).size())
                .as("Die Wiederholung darf Graph nicht erreichen – sonst entsteht der zweite Termin")
                .isEqualTo(1);
    }

    // ─────────────────────────── Stornierung und Umbuchung ───────────────────────────

    @Test(description = "Stornierung gibt den Slot wieder frei")
    public void stornierungGibtSlotFrei() {
        graphAcceptsAppointments();
        GRAPH_MOCK.stubFor(delete(urlPathMatching(GRAPH_TERMIN_PFAD + "/.*"))
                .willReturn(aResponse().withStatus(204)));

        buchen(request(zeit("2026-08-03T10:00:00", "UTC"),
                zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        restTemplate.exchange(EIGENE_API + "/" + TERMIN_ID, HttpMethod.DELETE, new HttpEntity<>(authHeaders), String.class);

        ResponseEntity<String> erneut = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        assertThat(erneut.getStatusCode())
                .as("Ohne Freigabe bei Stornierung bliebe der Slot für immer blockiert")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test(description = "Umbuchung gibt den alten Slot frei und belegt den neuen")
    public void umbuchungTauschtDieSlots() {
        graphAcceptsAppointments();
        GRAPH_MOCK.stubFor(patch(urlPathMatching(GRAPH_TERMIN_PFAD + "/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(appointmentJson())));

        buchen(request(zeit("2026-08-03T10:00:00", "UTC"),
                zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        restTemplate.exchange(EIGENE_API + "/" + TERMIN_ID, HttpMethod.PUT, new HttpEntity<>(
                request(zeit("2026-08-03T14:00:00", "UTC"),
                        zeit("2026-08-03T15:00:00", "UTC"), MITARBEITER_A), authHeaders), String.class);

        ResponseEntity<String> alterSlot = buchen(request(
                zeit("2026-08-03T10:00:00", "UTC"), zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        assertThat(alterSlot.getStatusCode())
                .as("Der alte Slot muss nach der Umbuchung wieder frei sein")
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> neuerSlot = buchen(request(
                zeit("2026-08-03T14:00:00", "UTC"), zeit("2026-08-03T15:00:00", "UTC"), MITARBEITER_A));
        assertThat(neuerSlot.getStatusCode())
                .as("Der neue Slot muss nach der Umbuchung belegt sein")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test(description = "Abgewiesene Buchung erreicht Microsoft Graph nicht")
    public void abgewieseneBuchungErreichtGraphNicht() {
        graphAcceptsAppointments();

        buchen(request(zeit("2026-08-03T10:00:00", "UTC"),
                zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));
        buchen(request(zeit("2026-08-03T10:00:00", "UTC"),
                zeit("2026-08-03T11:00:00", "UTC"), MITARBEITER_A));

        assertThat(GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_TERMIN_PFAD))).size())
                .as("Die zweite Anfrage wird vor dem Netzaufruf abgewiesen – spart Graph-Kontingent")
                .isEqualTo(1);
    }

    // ─────────────────────────── Hilfsmethoden ───────────────────────────

    private void graphAcceptsAppointments() {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(appointmentJson())));
    }

    private static String appointmentJson() {
        return """
                {
                  "id": "%s",
                  "serviceId": "%s",
                  "startDateTime": { "dateTime": "2026-08-03T10:00:00", "timeZone": "UTC" },
                  "endDateTime":   { "dateTime": "2026-08-03T11:00:00", "timeZone": "UTC" }
                }
                """.formatted(TERMIN_ID, DIENST_ID);
    }

    private ResponseEntity<String> buchen(CreateAppointmentRequest request) {
        return restTemplate.exchange(EIGENE_API, HttpMethod.POST, new HttpEntity<>(request, authHeaders), String.class);
    }

    private CreateAppointmentRequest request(DateTimeTimeZoneDto start,
                                             DateTimeTimeZoneDto ende,
                                             String... mitarbeiterIds) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setServiceId(DIENST_ID);
        request.setWorkerNames(List.of(mitarbeiterIds));
        request.setStartDateTime(start);
        request.setEndDateTime(ende);

        BookingCustomerInfoDto kunde = new BookingCustomerInfoDto();
        kunde.setName("Testkunde");
        kunde.setEmailAddress("kunde@example.de");
        request.setCustomers(List.of(kunde));

        return request;
    }

    private DateTimeTimeZoneDto zeit(String zeitstempel, String zone) {
        DateTimeTimeZoneDto dto = new DateTimeTimeZoneDto();
        dto.setDateTime(zeitstempel);
        dto.setTimeZone(zone);
        return dto;
    }
}
