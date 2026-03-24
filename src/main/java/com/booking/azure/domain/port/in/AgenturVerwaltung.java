package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.dto.request.CreateBookingBusinessRequest;

import java.util.List;

/**
 * Eingehender Port (Use-Case-Interface) für die Agenturverwaltung.
 *
 * Onion-Architektur – Domänenschicht:
 *   Definiert alle Use Cases rund um Buchungsbetriebe (Agenturen).
 *   Jede Agentur entspricht einem {@code BookingBusiness} im Microsoft-
 *   Tenant und hat eine eigene, dynamische Buchungs-URL nach dem Muster:
 *
 *   <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
 *
 *   Der Agenturname ist immer dynamisch und wird aus der ID des
 *   Buchungsbetriebs abgeleitet (z. B. {@code agenturfreiburg@midominio.com}).
 */
public interface AgenturVerwaltung {

    /**
     * Alle Buchungsbetriebe (Agenturen) des Mandanten auflisten.
     *
     * @return Liste aller Buchungsbetriebe inkl. berechneter Buchungs-URL
     */
    List<BookingBusinessDto> betriebeAuflisten();

    /**
     * Einen bestimmten Buchungsbetrieb anhand seiner ID abrufen.
     *
     * @param betriebId ID des Buchungsbetriebs (z. B. agentur@midominio.com)
     * @return Betriebsdaten inkl. dynamisch berechneter Buchungs-URL
     */
    BookingBusinessDto betriebAbrufen(String betriebId);

    /**
     * Einen neuen Buchungsbetrieb (Agentur) erstellen.
     *
     * @param anfrage Anfrage mit Anzeigename und weiteren Betriebsdaten
     * @return Der neu erstellte Betrieb
     */
    BookingBusinessDto betriebErstellen(CreateBookingBusinessRequest anfrage);

    /**
     * Einen bestehenden Buchungsbetrieb aktualisieren.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Anfrage mit den neuen Betriebsdaten
     * @return Der aktualisierte Betrieb
     */
    BookingBusinessDto betriebAktualisieren(String betriebId, CreateBookingBusinessRequest anfrage);

    /**
     * Einen Buchungsbetrieb dauerhaft löschen.
     *
     * @param betriebId ID des zu löschenden Buchungsbetriebs
     */
    void betriebLoeschen(String betriebId);

    /**
     * Die Buchungsseite einer Agentur veröffentlichen.
     * Nach der Veröffentlichung ist die Seite erreichbar unter:
     * <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
     *
     * @param betriebId ID des Buchungsbetriebs
     */
    void betriebVeroeffentlichen(String betriebId);

    /**
     * Die Buchungsseite einer Agentur deaktivieren (Veröffentlichung zurückziehen).
     * Setzt {@code isPublished = false} im Buchungsbetrieb.
     *
     * @param betriebId ID des Buchungsbetriebs
     */
    void betriebDeaktivieren(String betriebId);
}
