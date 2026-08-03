package com.booking.azure.e2e;

import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.support.GraphApiMockTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listing agencies across Entra ID directories.
 *
 * <h2>Why this cannot come from one call to Microsoft</h2>
 *
 * A token is scoped to a single tenant. Asking Graph for "all booking
 * businesses" returns whatever lives in that one directory — which, for this
 * platform, is none of the customers' agencies. The registration table is the
 * only place that knows the full set, and each agency is then read from its own
 * directory.
 */
public class AgencyListingE2ETest extends GraphApiMockTest {

    private static final String CLINIC_NAME = "Clínica Dental San Juan";
    private static final String CLINIC_BUSINESS_ID = "clinica-san-juan";
    private static final String CLINIC_TENANT = "1111-2222-3333";

    private static final String SPA_NAME = "Spa Las Palmas";
    private static final String SPA_BUSINESS_ID = "spa-las-palmas";
    private static final String SPA_TENANT = "4444-5555-6666";

    private static final String API_PATH = "/api/businesses";

    @Autowired
    private JdbcTemplate jdbc;

    /** Two agencies, each in its own directory. */
    @BeforeMethod(dependsOnMethods = "grundzustandHerstellen")
    public void registerTwoAgenciesInDifferentTenants() {
        jdbc.update("INSERT INTO agency_mapping (id, friendly_name, ms_tenant_id, ms_business_id) "
                + "VALUES (101, ?, ?, ?)", CLINIC_NAME, CLINIC_TENANT, CLINIC_BUSINESS_ID);
        jdbc.update("INSERT INTO agency_mapping (id, friendly_name, ms_tenant_id, ms_business_id) "
                + "VALUES (102, ?, ?, ?)", SPA_NAME, SPA_TENANT, SPA_BUSINESS_ID);
    }

    private String businessPath(String businessId) {
        return GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + businessId;
    }

    private void graphReturnsBusiness(String businessId, String displayName, String phone) {
        GRAPH_MOCK.stubFor(get(urlPathEqualTo(businessPath(businessId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "%s", "displayName": "%s", "phone": "%s", "isPublished": true}
                                """.formatted(businessId, displayName, phone))));
    }

    private List<BookingBusinessDto> list() {
        ResponseEntity<List<BookingBusinessDto>> response = restTemplate.exchange(
                API_PATH, HttpMethod.GET, new HttpEntity<>(authHeaders),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test(description = "Every registered agency is listed, each read from its own directory")
    public void listsAgenciesFromDifferentTenants() {
        graphReturnsBusiness(CLINIC_BUSINESS_ID, CLINIC_NAME, "+34910000000");
        graphReturnsBusiness(SPA_BUSINESS_ID, SPA_NAME, "+34920000000");

        List<BookingBusinessDto> businesses = list();

        assertThat(businesses)
                .describedAs("both agencies, although they live in different tenants")
                .extracting(BookingBusinessDto::getId)
                .contains(CLINIC_BUSINESS_ID, SPA_BUSINESS_ID);

        assertThat(businesses)
                .describedAs("details come from each agency's own directory")
                .extracting(BookingBusinessDto::getPhone)
                .contains("+34910000000", "+34920000000");
    }

    @Test(description = "The booking URL is filled in for every agency")
    public void everyAgencyCarriesItsBookingUrl() {
        graphReturnsBusiness(CLINIC_BUSINESS_ID, CLINIC_NAME, "+34910000000");
        graphReturnsBusiness(SPA_BUSINESS_ID, SPA_NAME, "+34920000000");

        assertThat(list())
                .allSatisfy(business -> assertThat(business.getBuchungsUrl()).isNotBlank());
    }

    @Test(description = "An agency whose directory is unreachable is still listed, "
            + "with the identifiers known locally")
    public void anUnreachableAgencyIsStillListed() {
        graphReturnsBusiness(CLINIC_BUSINESS_ID, CLINIC_NAME, "+34910000000");
        GRAPH_MOCK.stubFor(get(urlPathEqualTo(businessPath(SPA_BUSINESS_ID)))
                .willReturn(aResponse().withStatus(503)));

        List<BookingBusinessDto> businesses = list();

        assertThat(businesses)
                .describedAs("one unavailable directory must not shrink everyone else's list")
                .extracting(BookingBusinessDto::getId)
                .contains(CLINIC_BUSINESS_ID, SPA_BUSINESS_ID);

        BookingBusinessDto spa = businesses.stream()
                .filter(business -> SPA_BUSINESS_ID.equals(business.getId()))
                .findFirst().orElseThrow();

        assertThat(spa.getDisplayName())
                .describedAs("falls back to the locally registered name")
                .isEqualTo(SPA_NAME);
        assertThat(spa.getPhone())
                .describedAs("details are unknown, not invented")
                .isNull();
    }

    @Test(description = "Listing never asks the platform's own directory for the agency set")
    public void neverEnumeratesBusinessesFromASingleDirectory() {
        graphReturnsBusiness(CLINIC_BUSINESS_ID, CLINIC_NAME, "+34910000000");
        graphReturnsBusiness(SPA_BUSINESS_ID, SPA_NAME, "+34920000000");

        list();

        assertThat(GRAPH_MOCK.findAll(com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo(GRAPH_PRAEFIX + "/solutions/bookingBusinesses"))))
                .describedAs("a collection GET would only ever see one tenant's contents")
                .isEmpty();
    }
}
