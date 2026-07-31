package com.booking.azure.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClient-Konfiguration für HTTP-Anfragen an die Microsoft Graph API.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Stellt den reaktiven HTTP-Client für den {@code GraphApiClient} bereit.
 *
 * Zeitlimits stammen aus {@link GraphApiProperties} (Präfix {@code azure.graph}),
 * damit Tests sie herabsetzen können, ohne 30 Sekunden zu warten.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final GraphApiProperties properties;

    /**
     * Erstellt einen konfigurierten WebClient mit Zeitlimits für Graph-API-Anfragen.
     *
     * @return Fertig konfigurierter WebClient
     */
    @Bean
    public WebClient graphWebClient() {
        int antwortSekunden = (int) properties.getResponseTimeout().toSeconds();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(properties.getResponseTimeout())
                .doOnConnected(verbindung ->
                        verbindung.addHandlerLast(new ReadTimeoutHandler(antwortSekunden, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(antwortSekunden, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}


