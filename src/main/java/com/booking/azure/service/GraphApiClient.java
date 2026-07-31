package com.booking.azure.service;

import com.booking.azure.config.GraphApiProperties;
import com.booking.azure.domain.exception.GraphAntwortException;
import com.booking.azure.domain.exception.GraphUnbekanntException;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Infrastruktur-Adapter für die Microsoft Graph API.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Implementiert den ausgehenden Port {@link GraphApiAnfrage}.
 *   Kapselt alle HTTP-Kommunikation mit der Microsoft Graph API
 *   und injiziert automatisch den Bearer-Token (OAuth 2.0 Client Credentials)
 *   in jede Anfrage.
 *
 * <h2>Fehlerklassifikation</h2>
 *
 * Jeder Fehler wird einer von zwei Klassen zugeordnet – diese Unterscheidung
 * ist für die Vermeidung von Doppelbuchungen wesentlich:
 *
 * <ul>
 *   <li>{@link GraphAntwortException} – Graph hat geantwortet und abgelehnt.
 *       Gewissheit: es wurde nichts geschrieben.</li>
 *   <li>{@link GraphUnbekanntException} – keine Antwort erhalten.
 *       Der Schreibvorgang kann trotzdem stattgefunden haben.</li>
 * </ul>
 *
 * Wer diese beiden Fälle gleich behandelt, gibt bei einer Zeitüberschreitung
 * einen Slot frei, der in Wahrheit belegt ist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphApiClient implements GraphApiAnfrage {

    private final WebClient graphWebClient;
    private final GraphAuthService authService;
    private final GraphApiProperties properties;

    /**
     * HTTP-GET-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad (ohne Base-URL)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt
     */
    @Override
    public <T> T get(String pfad, Class<T> antwortTyp) {
        String url = properties.getBaseUrl() + pfad;
        log.debug("GET-Anfrage an Graph API: {}", url);

        return ausfuehren("GET", url, () -> graphWebClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::isError, antwort -> fehlerAbbilden(antwort))
                .bodyToMono(antwortTyp)
                .block());
    }

    /**
     * HTTP-POST-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt (wird als JSON serialisiert)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt
     */
    @Override
    public <T> T post(String pfad, Object koerper, Class<T> antwortTyp) {
        String url = properties.getBaseUrl() + pfad;
        log.debug("POST-Anfrage an Graph API: {}", url);

        return ausfuehren("POST", url, () -> graphWebClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(koerper)
                .retrieve()
                .onStatus(HttpStatusCode::isError, antwort -> fehlerAbbilden(antwort))
                .bodyToMono(antwortTyp)
                .block());
    }

    /**
     * HTTP-PATCH-Anfrage an die Graph API senden (Teilaktualisierung).
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt mit den zu ändernden Feldern
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt
     */
    @Override
    public <T> T patch(String pfad, Object koerper, Class<T> antwortTyp) {
        String url = properties.getBaseUrl() + pfad;
        log.debug("PATCH-Anfrage an Graph API: {}", url);

        return ausfuehren("PATCH", url, () -> graphWebClient.patch()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(koerper)
                .retrieve()
                .onStatus(HttpStatusCode::isError, antwort -> fehlerAbbilden(antwort))
                .bodyToMono(antwortTyp)
                .block());
    }

    /**
     * HTTP-DELETE-Anfrage an die Graph API senden.
     *
     * @param pfad Relativer API-Pfad der zu löschenden Ressource
     */
    @Override
    public void delete(String pfad) {
        String url = properties.getBaseUrl() + pfad;
        log.debug("DELETE-Anfrage an Graph API: {}", url);

        ausfuehren("DELETE", url, () -> graphWebClient.delete()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError, antwort -> fehlerAbbilden(antwort))
                .bodyToMono(Void.class)
                .block());
    }

    // ─────────────────────────────── Fehlerbehandlung ───────────────────────────────

    /**
     * Führt den Aufruf aus und ordnet jeden Fehler einer der beiden
     * Fehlerklassen zu.
     *
     * <p>Der {@code else}-Zweig behandelt alles Unerwartete bewusst als
     * <b>unbekannt</b>, nicht als Ablehnung. Das ist die sichere Richtung:
     * ein fälschlich blockierter Slot ist ein Ärgernis, eine fälschlich
     * freigegebene Reservierung eine Doppelbuchung.
     */
    private <T> T ausfuehren(String methode, String url, Supplier<T> aufruf) {
        try {
            return aufruf.get();

        } catch (GraphAntwortException ex) {
            log.error("Fehler bei {} {}: Graph antwortete mit Status {}", methode, url, ex.getStatus());
            throw ex;

        } catch (WebClientResponseException ex) {
            // Antwort erhalten – Ablehnung ist gewiss.
            log.error("Fehler bei {} {}: {} - {}", methode, url, ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw new GraphAntwortException(ex.getStatusCode().value(),
                    "Graph-API-Fehler: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);

        } catch (WebClientRequestException ex) {
            // Keine Antwort: Zeitüberschreitung, Verbindungsabbruch, DNS-Fehler.
            // Ob Graph geschrieben hat, ist unbekannt.
            log.error("Keine Antwort von Graph bei {} {}: {}", methode, url, ex.getMessage());
            throw new GraphUnbekanntException(
                    "Microsoft Graph hat auf " + methode + " nicht geantwortet. "
                            + "Ob die Operation ausgeführt wurde, ist unbekannt.", ex);

        } catch (GraphUnbekanntException ex) {
            throw ex;

        } catch (RuntimeException ex) {
            log.error("Unerwarteter Fehler bei {} {}: {}", methode, url, ex.toString());
            throw new GraphUnbekanntException(
                    "Unerwarteter Fehler bei " + methode + " an Microsoft Graph. "
                            + "Ob die Operation ausgeführt wurde, ist unbekannt.", ex);
        }
    }

    /** Baut aus einer Fehlerantwort von Graph eine {@link GraphAntwortException}. */
    private Mono<Throwable> fehlerAbbilden(org.springframework.web.reactive.function.client.ClientResponse antwort) {
        int status = antwort.statusCode().value();
        return antwort.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(koerper -> new GraphAntwortException(status,
                        "Graph-API-Fehler [" + status + "]: " + koerper));
    }
}
