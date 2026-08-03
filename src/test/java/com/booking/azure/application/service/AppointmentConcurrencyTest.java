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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 des Plans zur Vermeidung von Doppelbuchungen
 * (siehe {@code docs/PLAN-COLISION-RESERVAS.md}, §5, Phase 0).
 *
 * <h2>Diese Tests schlagen absichtlich fehl.</h2>
 *
 * Sie dokumentieren den heutigen Fehler: die Terminerstellung reicht jede
 * Anfrage ungeprüft an Microsoft Graph weiter. Es gibt keine
 * Verfügbarkeitsprüfung, keine Sperre, keine Idempotenz. Zwei gleichzeitige
 * Anfragen für denselben Slot erzeugen zwei gültige Termine.
 *
 * Erwartetes Verhalten <em>heute</em>:
 *   20 Anfragen → 20 × HTTP 201 → 20 POST-Aufrufe an Graph. Test ist ROT.
 *
 * Erwartetes Verhalten <em>nach Phase 2</em> (atomare Slot-Reservierung):
 *   20 Anfragen → 1 × HTTP 201, 19 × HTTP 409 → 1 POST-Aufruf an Graph. Test ist GRÜN.
 *
 * Die Tests laufen gegen eine per WireMock simulierte Graph API – kein
 * Azure-Mandant, keine Lizenzen, keine Kosten.
 */
public class AppointmentConcurrencyTest extends GraphApiMockTest {

    /**
     * Bewusst ohne '@': echte bookingBusiness-IDs sind E-Mail-Adressen, doch
     * deren URL-Kodierung ist ein eigenes Thema und würde hier nur vom
     * eigentlichen Prüfgegenstand ablenken.
     */
    private static final String BETRIEB_ID = "agenturtest";
    private static final String DIENST_ID = "5d1f2b3c-0000-0000-0000-000000000001";
    private static final String MITARBEITER_ID = "a1b2c3d4-0000-0000-0000-000000000002";

    private static final int GLEICHZEITIGE_ANFRAGEN = 20;

    /** Pfad, den {@code AppointmentService.terminErstellen} bei Graph aufruft. */
    private static final String GRAPH_TERMIN_PFAD =
            GRAPH_PRAEFIX + "/solutions/bookingBusinesses/" + BETRIEB_ID + "/appointments";


    @org.testng.annotations.BeforeMethod
    public void setupTest() {
        super.grundzustandHerstellen();
        String eigeneApiUrl = "/api/businesses/" + BETRIEB_ID + "/appointments";

        // Graph akzeptiert heute jede Anfrage – auch überlappende. Genau das
        // ist der administrative Endpunkt, den die Anwendung verwendet.
        GRAPH_MOCK.stubFor(post(urlPathEqualTo(GRAPH_TERMIN_PFAD))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "AAMkAGZlNTIzMTU3LWQ0M2QtNDQ0My05MzY2LTQ0MDU4MTMzZWI3Mg==",
                                  "serviceId": "%s",
                                  "startDateTime": { "dateTime": "2026-08-03T10:00:00", "timeZone": "Europe/Berlin" },
                                  "endDateTime":   { "dateTime": "2026-08-03T11:00:00", "timeZone": "Europe/Berlin" }
                                }
                                """.formatted(DIENST_ID))));
    }

    @Test(description = "20 gleichzeitige Anfragen auf denselben Slot dürfen nur einen Termin erzeugen")
    public void gleichzeitigeAnfragenAufDenselbenSlot() throws InterruptedException {
        List<ResponseEntity<String>> antworten = parallelBuchen(
                GLEICHZEITIGE_ANFRAGEN,
                nummer -> terminAnfrage("10:00:00", "11:00:00", "kunde" + nummer + "@example.de"));

        long angelegt = antworten.stream()
                .filter(a -> a.getStatusCode() == HttpStatus.CREATED)
                .count();
        long abgelehnt = antworten.stream()
                .filter(a -> a.getStatusCode() == HttpStatus.CONFLICT)
                .count();
        int anGraphGesendet =
                GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_TERMIN_PFAD))).size();

        assertThat(anGraphGesendet)
                .as("""
                        Nur die eine Anfrage, die die Slot-Reservierung gewinnt, darf Microsoft Graph \
                        erreichen. Ein Wert von %d bedeutet: es wurden %d überlappende Termine im \
                        Kalender des Mitarbeiters angelegt. Erwartet: 1.""",
                        anGraphGesendet, anGraphGesendet)
                .isEqualTo(1);

        assertThat(angelegt)
                .as("Genau eine Anfrage darf HTTP 201 erhalten")
                .isEqualTo(1);

        assertThat(abgelehnt)
                .as("Die übrigen %d Anfragen müssen mit HTTP 409 Conflict abgewiesen werden",
                        GLEICHZEITIGE_ANFRAGEN - 1)
                .isEqualTo(GLEICHZEITIGE_ANFRAGEN - 1L);
    }

    @Test(description = "Teilweise Überschneidung (10:00–11:00 gegen 10:30–11:30) muss abgewiesen werden")
    public void teilweiseUeberschneidung() {
        ResponseEntity<String> erste = buchen(
                terminAnfrage("10:00:00", "11:00:00", "erste@example.de"));
        ResponseEntity<String> zweite = buchen(
                terminAnfrage("10:30:00", "11:30:00", "zweite@example.de"));

        assertThat(erste.getStatusCode())
                .as("Die erste Buchung muss angenommen werden")
                .isEqualTo(HttpStatus.CREATED);

        assertThat(zweite.getStatusCode())
                .as("""
                        Der Slot 10:30–11:30 überschneidet sich mit dem bereits belegten Slot \
                        10:00–11:00 desselben Mitarbeiters. Eine Prüfung nur auf identische \
                        Startzeit übersieht diesen Fall – erforderlich ist eine Bereichsprüfung \
                        (EXCLUDE-Bedingung über tstzrange).""")
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(GRAPH_MOCK.findAll(postRequestedFor(urlPathEqualTo(GRAPH_TERMIN_PFAD))).size())
                .as("Nur die erste Buchung darf Graph erreichen")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hilfsmethoden
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Feuert {@code anzahl} Anfragen möglichst gleichzeitig ab.
     *
     * Ein Startgatter sorgt dafür, dass alle Threads erst laufen, wenn jeder
     * von ihnen bereitsteht. Ohne dieses Gatter starten die Threads gestaffelt
     * und das Zeitfenster für die Kollision wird nie getroffen.
     */
    private List<ResponseEntity<String>> parallelBuchen(
            int anzahl, java.util.function.IntFunction<CreateAppointmentRequest> anfrageBauer)
            throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(anzahl);
        CountDownLatch startGatter = new CountDownLatch(1);
        CountDownLatch fertig = new CountDownLatch(anzahl);
        ConcurrentLinkedQueue<ResponseEntity<String>> antworten = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < anzahl; i++) {
                final int nummer = i;
                pool.submit(() -> {
                    try {
                        startGatter.await();
                        antworten.add(buchen(anfrageBauer.apply(nummer)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        fertig.countDown();
                    }
                });
            }

            startGatter.countDown();
            assertThat(fertig.await(60, TimeUnit.SECONDS))
                    .as("Alle %d Anfragen müssen innerhalb von 60 s abgeschlossen sein", anzahl)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        return new ArrayList<>(antworten);
    }

    private ResponseEntity<String> buchen(CreateAppointmentRequest anfrage) {
        String url = "/api/businesses/" + BETRIEB_ID + "/appointments";
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(anfrage, authHeaders), String.class);
    }

    private CreateAppointmentRequest terminAnfrage(String startZeit, String endZeit, String kundenEmail) {
        CreateAppointmentRequest anfrage = new CreateAppointmentRequest();
        anfrage.setServiceId(DIENST_ID);
        anfrage.setWorkerNames(List.of("mitarbeiter-a"));
        anfrage.setStartDateTime(zeitpunkt(startZeit));
        anfrage.setEndDateTime(zeitpunkt(endZeit));

        BookingCustomerInfoDto kunde = new BookingCustomerInfoDto();
        kunde.setName("Testkunde");
        kunde.setEmailAddress(kundenEmail);
        anfrage.setCustomers(List.of(kunde));

        return anfrage;
    }

    private DateTimeTimeZoneDto zeitpunkt(String uhrzeit) {
        DateTimeTimeZoneDto dto = new DateTimeTimeZoneDto();
        dto.setDateTime("2026-08-03T" + uhrzeit);
        dto.setTimeZone("Europe/Berlin");
        return dto;
    }
}
