package com.booking.azure.service;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.booking.azure.config.GraphApiProperties;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Infrastruktur-Dienst für die Azure-AD-Authentifizierung.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Holt OAuth-2.0-Zugriffstoken von Microsoft Azure AD
 *   mittels des Client-Credentials-Flows (Anwendung-zu-Anwendung).
 *   Vorausgesetzter Azure-AD-Berechtigungsbereich: Bookings.ReadWrite.All
 *
 * Token-Caching:
 *   Tokens werden gecached und automatisch erneuert, wenn sie innerhalb
 *   der nächsten 5 Minuten ablaufen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphAuthService {

    private final GraphApiProperties properties;

    private ConfidentialClientApplication clientApplication;
    private String gecachtesToken;
    private long tokenAblaufzeit;

    /**
     * Gültiges Zugriffstoken für Microsoft Graph abrufen.
     * Verwendet den Cache und erneuert den Token automatisch vor Ablauf.
     *
     * @return Bearer-Token-String
     * @throws RuntimeException wenn der Token nicht abgerufen werden konnte
     */
    public String getAccessToken() {
        // Token wiederverwenden, wenn er noch mindestens 5 Minuten gültig ist
        if (gecachtesToken != null && System.currentTimeMillis() < tokenAblaufzeit - 300_000) {
            return gecachtesToken;
        }

        try {
            ConfidentialClientApplication app = getClientApplication();

            ClientCredentialParameters parameter = ClientCredentialParameters.builder(
                    Collections.singleton(properties.getScope()))
                    .build();

            CompletableFuture<IAuthenticationResult> future = app.acquireToken(parameter);
            IAuthenticationResult ergebnis = future.get();

            gecachtesToken = ergebnis.accessToken();
            tokenAblaufzeit = ergebnis.expiresOnDate().getTime();

            log.debug("Zugriffstoken erfolgreich abgerufen. Gültig bis: {}", ergebnis.expiresOnDate());
            return gecachtesToken;

        } catch (Exception e) {
            log.error("Fehler beim Abrufen des Azure-AD-Zugriffstokens: {}", e.getMessage(), e);
            throw new RuntimeException("Zugriffstoken von Azure AD konnte nicht abgerufen werden", e);
        }
    }

    /**
     * ConfidentialClientApplication-Instanz erstellen oder aus Cache abrufen.
     * Wird einmalig pro Anwendungslebenszyklus instanziiert.
     *
     * @return MSAL4J-Client-Instanz
     * @throws MalformedURLException bei ungültiger Authority-URL
     */
    private ConfidentialClientApplication getClientApplication() throws MalformedURLException {
        if (clientApplication == null) {
            String autoritaet = "https://login.microsoftonline.com/" + properties.getTenantId() + "/";
            clientApplication = ConfidentialClientApplication.builder(
                    properties.getClientId(),
                    ClientCredentialFactory.createFromSecret(properties.getClientSecret()))
                    .authority(autoritaet)
                    .build();
            log.info("Azure-AD-Client erstellt für Mandant: {}", properties.getTenantId());
        }
        return clientApplication;
    }
}
