package com.booking.azure.support;

import com.booking.azure.service.GraphAuthService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.mockito.Mockito.when;

/**
 * Basisklasse für Integrationstests gegen eine simulierte Microsoft Graph API
 * und ein echtes PostgreSQL.
 *
 * Zweck: vollständige Tests ohne Azure-Mandanten, ohne Lizenzen und ohne
 * ausgehenden Netzverkehr. Kosten: 0 €.
 *
 * <h2>Was hier abgeschnitten wird</h2>
 *
 * <ol>
 *   <li>{@code https://graph.microsoft.com} → WireMock auf zufälligem Port.
 *       Der {@code GraphApiClient} baut seine URL aus
 *       {@code azure.graph.base-url + Pfad}; diese Eigenschaft wird per
 *       {@link DynamicPropertySource} umgebogen.</li>
 *   <li>{@code https://login.microsoftonline.com} → {@link GraphAuthService}
 *       wird gemockt. Ohne diesen Schritt versucht MSAL4J einen echten Token
 *       abzurufen und der Test scheitert an fehlenden Zugangsdaten statt an
 *       der zu prüfenden Fachlogik.</li>
 * </ol>
 *
 * <h2>Warum echtes PostgreSQL und nicht H2</h2>
 *
 * H2 kennt weder {@code EXCLUDE USING gist} noch {@code tstzrange} noch die
 * Erweiterung {@code btree_gist}. Mit H2 existierte die Bedingung, die
 * Doppelbuchungen verhindert, im Test schlicht nicht – die Tests liefen grün,
 * während die Produktion kollidiert. Genau der Fehler, den diese Testreihe
 * aufdecken soll.
 */
@ActiveProfiles("test")
public abstract class GraphApiMockTest {

    /** Pfadpräfix, das der echten Graph-Basis-URL entspricht. */
    protected static final String GRAPH_PRAEFIX = "/v1.0";

    /** Simulierte Microsoft Graph API. Zufälliger Port, damit Tests parallel laufen können. */
    protected static final WireMockServer GRAPH_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    /**
     * Echtes PostgreSQL. Wird einmal je Test-JVM gestartet und von allen
     * Testklassen geteilt (statisch, ohne {@code @Container}), damit nicht
     * je Klasse ein neuer Container hochfährt.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("buchung_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        GRAPH_MOCK.start();
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(GRAPH_MOCK::stop));
    }

    @DynamicPropertySource
    static void testEigenschaften(DynamicPropertyRegistry registry) {
        registry.add("azure.graph.base-url", () -> GRAPH_MOCK.baseUrl() + GRAPH_PRAEFIX);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Ersetzt die echte Azure-AD-Authentifizierung (MSAL4J). */
    @MockBean
    protected GraphAuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void grundzustandHerstellen() {
        GRAPH_MOCK.resetAll();
        when(authService.getAccessToken()).thenReturn("test-token");

        // Der Container wird zwischen Tests geteilt; ohne dieses Leeren
        // blockierten Reservierungen aus vorherigen Tests die Slots.
        jdbcTemplate.execute("TRUNCATE TABLE slot_reservation RESTART IDENTITY");
    }
}
