package com.booking.azure.support;

import com.booking.azure.infrastructure.adapter.out.graph.GraphAuthService;
import com.booking.azure.infrastructure.security.JwtUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GraphApiMockTest extends AbstractTestNGSpringContextTests {

    protected static final String GRAPH_PRAEFIX = "/v1.0";
    protected static final WireMockServer GRAPH_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeSuite
    public static void startWireMock() {
        GRAPH_MOCK.start();
    }

    @AfterSuite
    public static void stopWireMock() {
        GRAPH_MOCK.stop();
    }

    @DynamicPropertySource
    static void testEigenschaften(DynamicPropertyRegistry registry) {
        registry.add("azure.graph.base-url", () -> GRAPH_MOCK.baseUrl() + GRAPH_PRAEFIX);
    }

    @MockBean
    protected GraphAuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;
    
    protected HttpHeaders authHeaders;

    @BeforeMethod
    public void grundzustandHerstellen() {
        GRAPH_MOCK.resetAll();
        when(authService.getAccessToken(any())).thenReturn("test-token");

        jdbcTemplate.execute("DELETE FROM slot_reservation");
        jdbcTemplate.execute("DELETE FROM staff_mapping");
        jdbcTemplate.execute("DELETE FROM agency_mapping");

        // We need to ensure the staff members we test against exist in STAFF_MAPPING, 
        // otherwise the SELECT FOR UPDATE pessimistic lock will fail to lock any rows.
        ensureStaffMemberExists("mitarbeiter-a");
        ensureStaffMemberExists("mitarbeiter-b");
        ensureStaffMemberExists("a1b2c3d4-0000-0000-0000-000000000002");

        // Generate JWT token for test requests
        String token = jwtUtil.generateAccessToken("testuser");
        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
    }

    private void ensureStaffMemberExists(String msStaffId) {
        int agencyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agency_mapping WHERE id = 1", Integer.class);
        if (agencyCount == 0) {
            jdbcTemplate.update("INSERT INTO agency_mapping (id, friendly_name, ms_tenant_id, ms_business_id) VALUES (1, 'agenturtest', 'tenant', 'agenturtest')");
        }
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM staff_mapping WHERE ms_staff_member_id = ?", Integer.class, msStaffId);
        if (count == 0) {
            jdbcTemplate.update("INSERT INTO staff_mapping (agency_id, ms_staff_member_id, friendly_name) VALUES (?, ?, ?)", 
                                1, msStaffId, msStaffId);
        }
    }
}
