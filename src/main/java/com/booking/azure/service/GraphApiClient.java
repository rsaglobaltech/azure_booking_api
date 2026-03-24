package com.booking.azure.service;

import com.booking.azure.domain.port.out.GraphApiAnfrage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.HttpMethod;
import com.microsoft.kiota.RequestInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Infrastruktur-Adapter für die Microsoft Graph API – implementiert via Graph SDK.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Implementiert den ausgehenden Port {@link GraphApiAnfrage}.
 *   Kapselt alle Kommunikation mit der Microsoft Graph API über den
 *   offiziellen Microsoft Graph Java SDK (v6).
 *
 * Authentifizierung:
 *   Erfolgt automatisch durch den {@link GraphServiceClient}, der intern
 *   einen {@code ClientSecretCredential} (azure-identity) verwendet.
 *   Tokens werden vom SDK gecacht und automatisch erneuert.
 *
 * HTTP-Schicht:
 *   Der SDK-interne {@code RequestAdapter} (OkHttp + Kiota) führt die
 *   eigentlichen HTTP-Anfragen durch. Antworten werden als {@link InputStream}
 *   abgerufen und via Jackson in die Ziel-DTOs deserialisiert.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphApiClient implements GraphApiAnfrage {

    /** Basis-URL der Microsoft Graph API v1.0 */
    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";

    private final GraphServiceClient graphServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * HTTP-GET-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad (ohne Base-URL)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt
     */
    @Override
    public <T> T get(String pfad, Class<T> antwortTyp) {
        log.debug("GET → {}{}", GRAPH_BASE_URL, pfad);
        try {
            RequestInformation requestInfo = buildRequest(HttpMethod.GET, pfad);
            InputStream stream = graphServiceClient.getRequestAdapter()
                    .sendPrimitive(requestInfo, null, InputStream.class);
            return objectMapper.readValue(stream, antwortTyp);
        } catch (ApiException apiEx) {
            log.error("Graph-API-Fehler bei GET {}: [{}] {}", pfad, apiEx.getResponseStatusCode(), apiEx.getMessage());
            throw new RuntimeException(
                    "Graph-API-Fehler [" + apiEx.getResponseStatusCode() + "]: " + apiEx.getMessage(), apiEx);
        } catch (Exception ex) {
            log.error("Fehler bei GET {}: {}", pfad, ex.getMessage(), ex);
            throw new RuntimeException("Graph-API GET Fehler: " + pfad, ex);
        }
    }

    /**
     * HTTP-POST-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt (wird als JSON serialisiert)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt, oder {@code null} bei leerem Body (204)
     */
    @Override
    public <T> T post(String pfad, Object koerper, Class<T> antwortTyp) {
        log.debug("POST → {}{}", GRAPH_BASE_URL, pfad);
        try {
            RequestInformation requestInfo = buildRequest(HttpMethod.POST, pfad);
            setJsonBody(requestInfo, koerper);
            InputStream stream = graphServiceClient.getRequestAdapter()
                    .sendPrimitive(requestInfo, null, InputStream.class);
            // 204 No Content oder leerer Body (z. B. publish/unpublish)
            if (stream == null) {
                return null;
            }
            return objectMapper.readValue(stream, antwortTyp);
        } catch (ApiException apiEx) {
            log.error("Graph-API-Fehler bei POST {}: [{}] {}", pfad, apiEx.getResponseStatusCode(), apiEx.getMessage());
            throw new RuntimeException(
                    "Graph-API-Fehler [" + apiEx.getResponseStatusCode() + "]: " + apiEx.getMessage(), apiEx);
        } catch (Exception ex) {
            log.error("Fehler bei POST {}: {}", pfad, ex.getMessage(), ex);
            throw new RuntimeException("Graph-API POST Fehler: " + pfad, ex);
        }
    }

    /**
     * HTTP-PATCH-Anfrage an die Graph API senden (Teilaktualisierung).
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt mit den zu ändernden Feldern
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @return Deserialisiertes Antwortobjekt, oder {@code null} bei leerem Body (204)
     */
    @Override
    public <T> T patch(String pfad, Object koerper, Class<T> antwortTyp) {
        log.debug("PATCH → {}{}", GRAPH_BASE_URL, pfad);
        try {
            RequestInformation requestInfo = buildRequest(HttpMethod.PATCH, pfad);
            setJsonBody(requestInfo, koerper);
            InputStream stream = graphServiceClient.getRequestAdapter()
                    .sendPrimitive(requestInfo, null, InputStream.class);
            if (stream == null) {
                return null;
            }
            return objectMapper.readValue(stream, antwortTyp);
        } catch (ApiException apiEx) {
            log.error("Graph-API-Fehler bei PATCH {}: [{}] {}", pfad, apiEx.getResponseStatusCode(), apiEx.getMessage());
            throw new RuntimeException(
                    "Graph-API-Fehler [" + apiEx.getResponseStatusCode() + "]: " + apiEx.getMessage(), apiEx);
        } catch (Exception ex) {
            log.error("Fehler bei PATCH {}: {}", pfad, ex.getMessage(), ex);
            throw new RuntimeException("Graph-API PATCH Fehler: " + pfad, ex);
        }
    }

    /**
     * HTTP-DELETE-Anfrage an die Graph API senden.
     *
     * @param pfad Relativer API-Pfad der zu löschenden Ressource
     */
    @Override
    public void delete(String pfad) {
        log.debug("DELETE → {}{}", GRAPH_BASE_URL, pfad);
        try {
            RequestInformation requestInfo = buildRequest(HttpMethod.DELETE, pfad);
            // sendPrimitive mit InputStream liefert null bei 204 No Content – für DELETE ausreichend
            graphServiceClient.getRequestAdapter()
                    .sendPrimitive(requestInfo, null, InputStream.class);
        } catch (ApiException apiEx) {
            log.error("Graph-API-Fehler bei DELETE {}: [{}] {}", pfad, apiEx.getResponseStatusCode(), apiEx.getMessage());
            throw new RuntimeException(
                    "Graph-API-Fehler [" + apiEx.getResponseStatusCode() + "]: " + apiEx.getMessage(), apiEx);
        } catch (Exception ex) {
            log.error("Fehler bei DELETE {}: {}", pfad, ex.getMessage(), ex);
            throw new RuntimeException("Graph-API DELETE Fehler: " + pfad, ex);
        }
    }

    // ──────────────────────────────── Hilfsmethoden ────────────────────────────

    /**
     * Baut ein {@link RequestInformation}-Objekt für die gegebene HTTP-Methode
     * und den relativen Pfad auf.
     */
    private RequestInformation buildRequest(HttpMethod method, String pfad) {
        RequestInformation requestInfo = new RequestInformation();
        requestInfo.httpMethod = method;
        requestInfo.urlTemplate = GRAPH_BASE_URL + pfad;
        requestInfo.headers.add("Accept", "application/json");
        return requestInfo;
    }

    /**
     * Serialisiert den Anfragekörper als JSON und setzt ihn im
     * {@link RequestInformation}-Objekt. Leere Objekte werden als {@code {}}
     * gesendet (Graph-API-Anforderung für leere POST-Anfragen wie publish/unpublish).
     */
    private void setJsonBody(RequestInformation requestInfo, Object body) {
        try {
            String json;
            if (body == null || (body instanceof String str && str.isBlank())) {
                json = "{}";
            } else {
                json = objectMapper.writeValueAsString(body);
            }
            requestInfo.setStreamContent(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    "application/json"
            );
        } catch (Exception ex) {
            throw new RuntimeException("Fehler beim Serialisieren des Anfragekörpers", ex);
        }
    }
}
