package com.booking.azure.e2e;

import com.booking.azure.application.command.CreateAppointmentRequest;
import com.booking.azure.dto.BookingAppointmentDto;
import com.booking.azure.dto.BookingCustomerInfoDto;
import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.booking.azure.support.GraphApiMockTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end walk through the booking flow, from the REST endpoint down to the
 * reservation row and the request that reaches Microsoft Graph.
 *
 * <h2>The scenario</h2>
 *
 * A customer picks the agency "Clínica Dental San Juan", the practitioner
 * "Dr. Pérez" and the 10:00 slot on 15 August 2026, local time in Madrid.
 *
 * <h2>What the caller sends, and what it becomes</h2>
 *
 * The public API takes the <b>agency name</b> in the path and <b>practitioner
 * names</b> in the body — never Microsoft identifiers. Translating those into
 * {@code ms_business_id} and {@code ms_staff_member_id} is the
 * {@code Agency} aggregate's job, and it is what keeps Microsoft's identifiers
 * out of the public contract.
 */
public class BookingFlowE2ETest extends GraphApiMockTest {

    private static final String AGENCY_NAME = "Clínica Dental San Juan";
    private static final String MS_BUSINESS_ID = "clinica-san-juan";
    private static final String MS_TENANT_ID = "1111-2222-3333";

    private static final String DOCTOR_NAME = "Dr. Pérez";
    private static final String MS_STAFF_ID = "dr-perez-ms-id";

    private static final String SERVICE_ID = "limpieza-dental";
    private static final String ZONE = "Europe/Madrid";
    private static final String SLOT_START = "2026-08-15T10:00:00";
    private static final String SLOT_END = "2026-08-15T11:00:00";

    private static final String GRAPH_APPOINTMENTS =
            GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + MS_BUSINESS_ID + "/appointments";

    private static final String API_PATH = "/api/businesses/{agency}/appointments";

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Registers the clinic and its practitioner.
     *
     * This is what a real onboarding would have written when the clinic signed
     * up and its administrator granted admin consent.
     */
    @BeforeMethod(dependsOnMethods = "grundzustandHerstellen")
    public void registerClinic() {
        jdbc.update("INSERT INTO agency_mapping (id, friendly_name, ms_tenant_id, ms_business_id) "
                + "VALUES (99, ?, ?, ?)", AGENCY_NAME, MS_TENANT_ID, MS_BUSINESS_ID);
        jdbc.update("INSERT INTO staff_mapping (agency_id, ms_staff_member_id, friendly_name) "
                + "VALUES (99, ?, ?)", MS_STAFF_ID, DOCTOR_NAME);
    }

    // ───────────────────────────────── helpers ─────────────────────────────────

    private void graphAcceptsAppointment(String appointmentId) {
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_APPOINTMENTS))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "serviceId": "%s",
                                  "staffMemberIds": ["%s"],
                                  "startDateTime": {"dateTime": "%s", "timeZone": "%s"},
                                  "endDateTime": {"dateTime": "%s", "timeZone": "%s"}
                                }
                                """.formatted(appointmentId, SERVICE_ID, MS_STAFF_ID,
                                SLOT_START, ZONE, SLOT_END, ZONE))));
    }

    private CreateAppointmentRequest bookingRequest() {
        DateTimeTimeZoneDto start = new DateTimeTimeZoneDto();
        start.setDateTime(SLOT_START);
        start.setTimeZone(ZONE);

        DateTimeTimeZoneDto end = new DateTimeTimeZoneDto();
        end.setDateTime(SLOT_END);
        end.setTimeZone(ZONE);

        BookingCustomerInfoDto customer = new BookingCustomerInfoDto();
        customer.setName("Raul");
        customer.setEmailAddress("raul@email.com");

        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setServiceId(SERVICE_ID);
        request.setStartDateTime(start);
        request.setEndDateTime(end);
        request.setWorkerNames(List.of(DOCTOR_NAME));
        request.setCustomers(List.of(customer));
        return request;
    }

    private ResponseEntity<BookingAppointmentDto> book(String agencyName) {
        return restTemplate.exchange(API_PATH, HttpMethod.POST,
                new HttpEntity<>(bookingRequest(), authHeaders),
                BookingAppointmentDto.class, agencyName);
    }

    private List<Map<String, Object>> reservations() {
        return jdbc.queryForList("SELECT * FROM slot_reservation");
    }

    // ───────────────────────────────── the flow ────────────────────────────────

    @Test(description = "The happy path: the booking is held locally, written to Graph, "
            + "and only then confirmed")
    public void bookingIsHeldLocallyThenWrittenToGraph() {
        graphAcceptsAppointment("appointment-001");

        ResponseEntity<BookingAppointmentDto> response = book(AGENCY_NAME);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("appointment-001");

        List<Map<String, Object>> rows = reservations();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("STATE")).isEqualTo("CONFIRMED");
        assertThat(row.get("GRAPH_APPOINTMENT_ID")).isEqualTo("appointment-001");
        assertThat(row.get("STAFF_MEMBER_ID")).isEqualTo(MS_STAFF_ID);
        assertThat(row.get("BUSINESS_ID")).isEqualTo(MS_BUSINESS_ID);
        assertThat(row.get("BOOKING_ID"))
                .describedAs("every reservation belongs to a booking aggregate")
                .isNotNull();
    }

    @Test(description = "The practitioner name is translated into the Microsoft identifier, "
            + "and the caller's local time is preserved on the way out")
    public void graphReceivesMicrosoftIdentifiersAndLocalTime() {
        graphAcceptsAppointment("appointment-002");

        book(AGENCY_NAME);

        var sent = GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_APPOINTMENTS)));
        assertThat(sent).hasSize(1);
        String body = sent.get(0).getBodyAsString();

        assertThat(body)
                .describedAs("the resolved Microsoft id, not the human-readable name")
                .contains(MS_STAFF_ID)
                .doesNotContain(DOCTOR_NAME);

        assertThat(body)
                .describedAs("10:00 in Madrid stays 10:00 in Madrid, it is not rewritten to UTC")
                .contains("\"dateTime\":\"2026-08-15T10:00:00\"")
                .contains("\"timeZone\":\"Europe/Madrid\"");

        assertThat(body)
                .describedAs("workerNames is an internal concept and must not leak to Graph")
                .doesNotContain("workerNames");
    }

    @Test(description = "The slot is stored as the right instant, so overlaps stay comparable "
            + "across zones")
    public void theSlotIsStoredAsAnAbsoluteInstant() {
        graphAcceptsAppointment("appointment-003");

        book(AGENCY_NAME);

        // Read as OffsetDateTime, not Timestamp: a Timestamp is rendered in the
        // JVM's own zone, which would make this assertion pass or fail depending
        // on where the test happens to run.
        OffsetDateTime start = jdbc.queryForObject(
                "SELECT start_utc FROM slot_reservation", OffsetDateTime.class);
        OffsetDateTime end = jdbc.queryForObject(
                "SELECT end_utc FROM slot_reservation", OffsetDateTime.class);

        // 10:00 in Madrid in August is CEST (UTC+2), so the instant is 08:00Z.
        assertThat(start).isNotNull();
        assertThat(start.toInstant()).isEqualTo(Instant.parse("2026-08-15T08:00:00Z"));
        assertThat(end.toInstant()).isEqualTo(Instant.parse("2026-08-15T09:00:00Z"));
    }

    @Test(description = "A second booking for the same practitioner and slot is rejected "
            + "with 409, and Graph is never asked")
    public void theSameSlotCannotBeBookedTwice() {
        graphAcceptsAppointment("appointment-004");
        assertThat(book(AGENCY_NAME).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.exchange(API_PATH, HttpMethod.POST,
                new HttpEntity<>(bookingRequest(), authHeaders), String.class, AGENCY_NAME);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_APPOINTMENTS))))
                .describedAs("the conflict is decided locally, before any call to Graph")
                .hasSize(1);
    }

    // ─────────────────────────── what the question asked ───────────────────────

    @Test(description = "An unknown agency is answered with 404 before anything is reserved "
            + "or sent to Graph")
    public void unknownAgencyIsRejectedWithoutSideEffects() {
        graphAcceptsAppointment("never-created");

        ResponseEntity<String> response = restTemplate.exchange(API_PATH, HttpMethod.POST,
                new HttpEntity<>(bookingRequest(), authHeaders), String.class,
                "Clínica Que No Existe");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .describedAs("the error body names what was missing")
                .contains("Agency not found");

        assertThat(reservations())
                .describedAs("resolveAgency throws before any slot is held")
                .isEmpty();
        assertThat(GRAPH_MOCK.findAll(anyRequestedFor(anyUrl())))
                .describedAs("no external call is made for an agency we do not know")
                .isEmpty();
    }

    @Test(description = "A practitioner unknown to that agency is also 404, with nothing reserved")
    public void unknownPractitionerIsRejectedWithoutSideEffects() {
        graphAcceptsAppointment("never-created");

        CreateAppointmentRequest request = bookingRequest();
        request.setWorkerNames(List.of("Dra. Inexistente"));

        ResponseEntity<String> response = restTemplate.exchange(API_PATH, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders), String.class, AGENCY_NAME);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Staff member not found");
        assertThat(reservations()).isEmpty();
        assertThat(GRAPH_MOCK.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test(description = "An agency belonging to another tenant is not reachable by name alone")
    public void agencyOfAnotherTenantIsNotResolvedByAnotherName() {
        graphAcceptsAppointment("never-created");

        // 'agenturtest' exists (seeded by the base class) but has no practitioner
        // called Dr. Pérez: staff resolution is scoped to the agency aggregate.
        ResponseEntity<String> response = restTemplate.exchange(API_PATH, HttpMethod.POST,
                new HttpEntity<>(bookingRequest(), authHeaders), String.class, "agenturtest");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reservations()).isEmpty();
    }
}
