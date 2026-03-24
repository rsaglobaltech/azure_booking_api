package com.booking.azure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient-Konfiguration für HTTP-Anfragen an die Microsoft Graph API.
 *
 * Onion-Architektur – Infrastrukturschicht:
 *   Stellt den reaktiven HTTP-Client für den {@code GraphApiClient} bereit.
 *
 * Zeitlimits:
 *   - Verbindungsaufbau: 10 Sekunden
 *   - Antwort lesen:     30 Sekunden
 *   - Anfrage schreiben: 30 Sekunden
 */
@Configuration
public class WebClientConfig {

    /**
     * Erstellt einen konfigurierten WebClient mit Timeouts für Graph-API-Anfragen.
     *
     * @return Fertig konfigurierter WebClient
     */
    @Bean
    public WebClient graphWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)    // 10 Sekunden Verbindungsaufbau
                .responseTimeout(Duration.ofSeconds(30))                  // 30 Sekunden Antwort-Timeout
                .doOnConnected(verbindung ->
                        verbindung.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
