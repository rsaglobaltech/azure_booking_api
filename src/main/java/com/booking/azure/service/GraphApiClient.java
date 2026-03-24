package com.booking.azure.service;

import com.booking.azure.config.GraphApiProperties;
import com.booking.azure.domain.port.out.GraphApiAnfrage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Infrastruktur-Adapter für die Microsoft Graph API.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Implementiert den ausgehenden Port {@link GraphApiAnfrage}.
 *   Kapselt alle HTTP-Kommunikation mit der Microsoft Graph API
 *   und injiziert automatisch den Bearer-Token (OAuth 2.0 Client Credentials)
 *   in jede Anfrage.
 *
 * Authentifizierung: Azure AD → Client-Credentials-Flow via MSAL4J
 * Timeout: 10 s Verbindungsaufbau, 30 s Lesen/Schreiben
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
        try {
            return graphWebClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .onStatus(status -> status.isError(), antwort ->
                            antwort.bodyToMono(String.class)
                                    .flatMap(koerper -> Mono.error(
                                            new RuntimeException("Graph-API-Fehler [" + antwort.statusCode() + "]: " + koerper))))
                    .bodyToMono(antwortTyp)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fehler bei GET {}: {} - {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Graph-API-Fehler: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        }
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
        try {
            return graphWebClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(koerper)
                    .retrieve()
                    .onStatus(status -> status.isError(), antwort ->
                            antwort.bodyToMono(String.class)
                                    .flatMap(b -> Mono.error(
                                            new RuntimeException("Graph-API-Fehler [" + antwort.statusCode() + "]: " + b))))
                    .bodyToMono(antwortTyp)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fehler bei POST {}: {} - {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Graph-API-Fehler: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        }
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
        try {
            return graphWebClient.patch()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(koerper)
                    .retrieve()
                    .onStatus(status -> status.isError(), antwort ->
                            antwort.bodyToMono(String.class)
                                    .flatMap(b -> Mono.error(
                                            new RuntimeException("Graph-API-Fehler [" + antwort.statusCode() + "]: " + b))))
                    .bodyToMono(antwortTyp)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fehler bei PATCH {}: {} - {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Graph-API-Fehler: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        }
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
        try {
            graphWebClient.delete()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                    .retrieve()
                    .onStatus(status -> status.isError(), antwort ->
                            antwort.bodyToMono(String.class)
                                    .flatMap(b -> Mono.error(
                                            new RuntimeException("Graph-API-Fehler [" + antwort.statusCode() + "]: " + b))))
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fehler bei DELETE {}: {} - {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Graph-API-Fehler: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        }
    }
}
