package com.booking.azure.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS-Konfiguration (Cross-Origin Resource Sharing).
 *
 * Erlaubt Anfragen von allen Ursprüngen auf den Pfad {@code /api/**}.
 * In Produktionsumgebungen sollte {@code allowedOrigins} auf
 * vertrauenswürdige Domänen eingeschränkt werden.
 */
@Configuration
public class CorsConfig {

    /**
     * Registriert die CORS-Regeln für alle API-Endpunkte.
     *
     * Erlaubte Methoden: GET, POST, PUT, DELETE, OPTIONS
     * Erlaubte Ursprünge: alle (*) – in Produktion einschränken
     *
     * @return Konfigurierter WebMvcConfigurer
     */
    @Bean
    public WebMvcConfigurer corsKonfigurierer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}


