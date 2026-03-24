package com.booking.azure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

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
}
