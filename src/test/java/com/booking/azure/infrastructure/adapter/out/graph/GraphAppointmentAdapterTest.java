package com.booking.azure.infrastructure.adapter.out.graph;

import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.domain.model.AppointmentDraft;
import com.booking.azure.domain.model.vo.AppointmentCustomer;
import com.booking.azure.domain.model.vo.AppointmentId;
import com.booking.azure.domain.model.vo.BusinessId;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.model.vo.ServiceId;
import com.booking.azure.domain.model.vo.ServiceLocation;
import com.booking.azure.domain.model.vo.StaffMemberId;
import com.booking.azure.domain.model.vo.TimeWindow;
import com.booking.azure.dto.BookingAppointmentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The anti-corruption layer's translation, pinned down.
 *
 * The existing WireMock tests only count requests, never inspect their bodies,
 * so nothing else in the suite would notice if the payload sent to Microsoft
 * Graph quietly changed shape.
 */
public class GraphAppointmentAdapterTest {

    private static final BusinessId BUSINESS = BusinessId.of("business-1");
    private static final Instant START = Instant.parse("2026-03-01T08:00:00Z");
    private static final Instant END = Instant.parse("2026-03-01T09:00:00Z");

    /** Captures whatever the adapter hands to the HTTP client. */
    static final class CapturingGraph implements GraphApiRequest {
        Object body;
        String path;

        @Override
        public <T> T get(String path, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T post(String path, Object body, Class<T> responseType) {
            this.path = path;
            this.body = body;
            return null;
        }

        @Override
        public <T> T patch(String path, Object body, Class<T> responseType) {
            this.path = path;
            this.body = body;
            return null;
        }

        @Override
        public void delete(String path) {
            this.path = path;
        }
    }

    private CapturingGraph graph;
    private GraphAppointmentAdapter adapter;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setUp() {
        graph = new CapturingGraph();
        adapter = new GraphAppointmentAdapter(graph);
        mapper = new ObjectMapper();
    }

    private AppointmentDraft draft(ZoneId zone) {
        return new AppointmentDraft(
                ServiceId.of("service-1"),
                TimeWindow.of(START, END),
                zone,
                List.of(StaffMemberId.of("staff-a")),
                null, null, null, null, null, null);
    }

    private String sentJson() throws Exception {
        return mapper.writeValueAsString(graph.body);
    }

    @Test(description = "The window is rendered back into the zone the caller booked in, "
            + "not rewritten into UTC")
    public void rendersTimesInTheCallersZone() throws Exception {
        adapter.create(BUSINESS, draft(ZoneId.of("Europe/Berlin")));

        String json = sentJson();
        // 08:00Z is 09:00 in Berlin on this date (CET, UTC+1)
        assertThat(json).contains("\"dateTime\":\"2026-03-01T09:00:00\"");
        assertThat(json).contains("\"timeZone\":\"Europe/Berlin\"");
        assertThat(json).contains("\"dateTime\":\"2026-03-01T10:00:00\"");
    }

    @Test(description = "A UTC draft renders as UTC")
    public void rendersUtcWhenThatIsTheCallersZone() throws Exception {
        adapter.create(BUSINESS, draft(ZoneOffset.UTC));

        assertThat(sentJson())
                .contains("\"dateTime\":\"2026-03-01T08:00:00\"")
                .contains("\"timeZone\":\"Z\"");
    }

    @Test(description = "Absent optional fields are omitted rather than sent as null")
    public void omitsAbsentFields() throws Exception {
        adapter.create(BUSINESS, draft(ZoneOffset.UTC));

        assertThat(sentJson())
                .doesNotContain("customers")
                .doesNotContain("serviceNotes")
                .doesNotContain("serviceLocation")
                .doesNotContain("null");
    }

    @Test(description = "The internal worker names never reach Graph")
    public void sendsResolvedStaffIdsOnly() throws Exception {
        assertThat(sentJsonOf(draft(ZoneOffset.UTC)))
                .contains("\"staffMemberIds\":[\"staff-a\"]")
                .doesNotContain("workerNames");
    }

    private String sentJsonOf(AppointmentDraft draft) throws Exception {
        adapter.create(BUSINESS, draft);
        return sentJson();
    }

    @Test(description = "Customer details are carried through in full")
    public void translatesTheCustomer() throws Exception {
        AppointmentDraft withCustomer = new AppointmentDraft(
                ServiceId.of("service-1"),
                TimeWindow.of(START, END),
                ZoneOffset.UTC,
                List.of(StaffMemberId.of("staff-a")),
                new AppointmentCustomer(
                        CustomerContact.of("Ada", "ada@example.com"), "cust-1", "+34600000000", "window seat"),
                null, null, null, null, null);

        adapter.create(BUSINESS, withCustomer);

        assertThat(sentJson())
                .contains("\"name\":\"Ada\"")
                .contains("\"emailAddress\":\"ada@example.com\"")
                .contains("\"customerId\":\"cust-1\"")
                .contains("\"phone\":\"+34600000000\"")
                .contains("\"notes\":\"window seat\"");
    }

    @Test(description = "A location without an address omits the address node entirely")
    public void translatesLocationWithoutAddress() throws Exception {
        AppointmentDraft withLocation = new AppointmentDraft(
                ServiceId.of("service-1"),
                TimeWindow.of(START, END),
                ZoneOffset.UTC,
                List.of(StaffMemberId.of("staff-a")),
                null, null, null,
                new ServiceLocation("Room 3", null, null, null, null, null),
                null, null);

        adapter.create(BUSINESS, withLocation);

        assertThat(sentJson())
                .contains("\"displayName\":\"Room 3\"")
                .doesNotContain("address");
    }

    @Test(description = "Paths follow the Graph appointment resource layout")
    public void buildsTheExpectedPaths() {
        adapter.create(BUSINESS, draft(ZoneOffset.UTC));
        assertThat(graph.path).isEqualTo("/solutions/bookingBusinesses/business-1/appointments");

        adapter.cancel(BUSINESS, AppointmentId.of("appointment-9"));
        assertThat(graph.path)
                .isEqualTo("/solutions/bookingBusinesses/business-1/appointments/appointment-9");
    }

    @Test(description = "Update targets the individual appointment")
    public void updateTargetsTheAppointment() {
        BookingAppointmentDto ignored =
                adapter.update(BUSINESS, AppointmentId.of("appointment-9"), draft(ZoneOffset.UTC));

        assertThat(graph.path)
                .isEqualTo("/solutions/bookingBusinesses/business-1/appointments/appointment-9");
    }
}
