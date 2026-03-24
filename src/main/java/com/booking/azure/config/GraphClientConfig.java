package com.booking.azure.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring-Konfiguration für den Microsoft Graph SDK Client.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Erstellt einen {@link GraphServiceClient}-Bean, der von der
 *   gesamten Infrastrukturschicht als zentraler API-Einstiegspunkt
 *   verwendet wird.
 *
 * Authentifizierung:
 *   Verwendet den OAuth-2.0-Client-Credentials-Flow über
 *   {@code azure-identity} ({@link ClientSecretCredential}).
 *   Der Scope {@code https://graph.microsoft.com/.default} erteilt
 *   alle für die registrierte App konfigurierten API-Berechtigungen.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GraphClientConfig {

    private final GraphApiProperties properties;

    /**
     * Erstellt den zentralen {@link GraphServiceClient} mit
     * Client-Credentials-Authentifizierung via azure-identity.
     *
     * @return Fertig authentifizierter Graph-SDK-Client
     */
    @Bean
    public GraphServiceClient graphServiceClient() {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(properties.getTenantId())
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .build();

        log.info("GraphServiceClient initialisiert für Mandant: {}", properties.getTenantId());
        return new GraphServiceClient(credential, properties.getScope());
    }
}
