package com.booking.azure.dto.request;

import com.booking.azure.dto.DateTimeTimeZoneDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Vereinfachte Anfrage zum Erstellen eines Termins.
 *
 * Der Client sendet nur fachlich relevante Felder:
 *   - Zeitraum (start / end)
 *   - Dienstort (location)
 *   - Benachrichtigungseinstellung
 *   - Kundendaten (Vor-/Nachname, E-Mail, Adresse, Telefon optional, Anmerkungen optional)
 *
 * Das interne Mapping auf die vollständige Microsoft-Graph-API-Struktur
 * (staffMemberIds, customers-Array, serviceLocation, customerName, etc.)
 * übernimmt der {@code AppointmentService}.
 */
@Data
public class CreateAppointmentRequest {

    /** ID (GUID) des zu buchenden Dienstes */
    @NotBlank(message = "serviceId es obligatorio")
    @JsonProperty("serviceId")
    private String serviceId;

    /** Startzeitpunkt des Termins (Datum + Zeitzone) */
    @NotNull(message = "start es obligatorio")
    @Valid
    @JsonProperty("start")
    private DateTimeTimeZoneDto start;

    /** Endzeitpunkt des Termins (Datum + Zeitzone) */
    @NotNull(message = "end es obligatorio")
    @Valid
    @JsonProperty("end")
    private DateTimeTimeZoneDto end;

    /**
     * Dienstadresse (serviceLocation).
     * Wird als serviceLocation und als Kundenadresse an die Graph-API übergeben.
     */
    @NotNull(message = "location es obligatorio")
    @Valid
    @JsonProperty("location")
    private AdresseRequest location;

    /**
     * ID (GUID) des Mitarbeiters, der den Termin betreut.
     * Wird über {@code GET /api/businesses/{id}/staff/search?q=...} ermittelt.
     *
     * Wenn angegeben, wird dieser Wert verwendet.
     * Wenn leer / nicht angegeben, greift die Konfiguration {@code buchung.default-staff-ids}.
     */
    @JsonProperty("mitarbeiterId")
    private String mitarbeiterId;

    /**
     * Ob der Kunde per E-Mail/SMS benachrichtigt werden soll.
     * Standard: {@code true}
     */
    @JsonProperty("notificationsEnabled")
    private boolean notificationsEnabled = true;

    /** Kundendaten für den Termin */
    @NotNull(message = "kunde es obligatorio")
    @Valid
    @JsonProperty("kunde")
    private KundeRequest kunde;

    // ─────────────────────────────── Eingebettete Klassen ───────────────────────

    /**
     * Kundendaten, die der Aufrufer mitschickt.
     */
    @Data
    public static class KundeRequest {

        @NotBlank(message = "vorname es obligatorio")
        @JsonProperty("vorname")
        private String vorname;

        @NotBlank(message = "nachname es obligatorio")
        @JsonProperty("nachname")
        private String nachname;

        @NotBlank(message = "email es obligatorio")
        @Email(message = "email muss eine gültige E-Mail-Adresse sein")
        @JsonProperty("email")
        private String email;

        /** Optionale Telefonnummer */
        @JsonProperty("telefon")
        private String telefon;

        /** Optionale Anmerkungen / Hinweise des Kunden */
        @JsonProperty("anmerkungen")
        private String anmerkungen;
    }

    /**
     * Vereinfachte Adresse für den Dienstort.
     */
    @Data
    public static class AdresseRequest {

        @NotBlank(message = "strasse es obligatorio")
        @JsonProperty("strasse")
        private String strasse;

        @NotBlank(message = "ort es obligatorio")
        @JsonProperty("ort")
        private String ort;

        @NotBlank(message = "plz es obligatorio")
        @JsonProperty("plz")
        private String plz;

        /** Land – Standard: Deutschland */
        @JsonProperty("land")
        private String land = "Deutschland";
    }
}

