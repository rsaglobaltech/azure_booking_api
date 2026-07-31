package com.booking.azure.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

import java.time.Duration;

/**
 * Konfigurationseigenschaften für die Microsoft Graph API und Azure AD.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Liest Konfigurationswerte aus {@code application.yml} (Präfix: {@code azure.graph}).
 *
 * Werte werden über Umgebungsvariablen gesetzt:
 *   - AZURE_TENANT_ID    → tenantId
 *   - AZURE_CLIENT_ID    → clientId
 *   - AZURE_CLIENT_SECRET → clientSecret
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "azure.graph")
public class GraphApiProperties {

    /** Azure-AD-Mandanten-ID (Tenant-ID) */
    private String tenantId;

    /** Client-ID der registrierten Azure-AD-Anwendung */
    private String clientId;

    /** Client-Secret der registrierten Azure-AD-Anwendung */
    private String clientSecret;

    /** OAuth-2.0-Berechtigungsbereich für Microsoft Graph */
    private String scope;

    /** Basis-URL der Microsoft Graph API */
    private String baseUrl;

    /** Zeitlimit für den Verbindungsaufbau. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * Zeitlimit für die Antwort von Graph.
     *
     * <p>Ein Zeitlimit ist <b>kein</b> Mittel des gegenseitigen Ausschlusses.
     * Es begrenzt nur die Wartezeit – und erzeugt bei jedem Auslösen einen Fall
     * mit unbekanntem Ausgang ({@code GraphUnknownException}).
     *
     * <p>Eine Senkung auf ~10 s ist vorgesehen, aber erst <b>nach</b> Einführung
     * der Idempotenz (Phase 1): mehr Zeitüberschreitungen bedeuten mehr
     * Wiederholungen, und ohne Idempotenz erhalten die Kunden dafür {@code 409}
     * statt ihres bereits angelegten Termins.
     */
    private Duration responseTimeout = Duration.ofSeconds(30);
}


