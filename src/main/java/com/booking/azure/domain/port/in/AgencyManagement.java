package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingBusinessDto;
import com.booking.azure.domain.command.CreateBookingBusinessRequest;

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
public interface AgencyManagement {

    /**
     * Alle Buchungsbetriebe (Agenturen) des Mandanten auflisten.
     *
     * @return Liste aller Buchungsbetriebe inkl. berechneter Buchungs-URL
     */
    List<BookingBusinessDto> listBusinesses();

    /**
     * Einen bestimmten Buchungsbetrieb anhand seiner ID abrufen.
     *
     * @param businessId ID des Buchungsbetriebs (z. B. agency@midominio.com)
     * @return Betriebsdaten inkl. dynamisch berechneter Buchungs-URL
     */
    BookingBusinessDto getBusiness(String businessId);

    /**
     * Einen neuen Buchungsbetrieb (Agentur) erstellen.
     *
     * @param request Anfrage mit Anzeigename und weiteren Betriebsdaten
     * @return Der neu erstellte Betrieb
     */
    BookingBusinessDto createBusiness(CreateBookingBusinessRequest request);

    /**
     * Einen bestehenden Buchungsbetrieb aktualisieren.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param request   Anfrage mit den neuen Betriebsdaten
     * @return Der aktualisierte Betrieb
     */
    BookingBusinessDto updateBusiness(String businessId, CreateBookingBusinessRequest request);

    /**
     * Einen Buchungsbetrieb dauerhaft löschen.
     *
     * @param businessId ID des zu löschenden Buchungsbetriebs
     */
    void deleteBusiness(String businessId);

    /**
     * Die Buchungsseite einer Agentur veröffentlichen.
     * Nach der Veröffentlichung ist die Seite erreichbar unter:
     * <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
     *
     * @param businessId ID des Buchungsbetriebs
     */
    void publishBusiness(String businessId);

    /**
     * Die Buchungsseite einer Agentur deaktivieren (Veröffentlichung zurückziehen).
     * Setzt {@code isPublished = false} im Buchungsbetrieb.
     *
     * @param businessId ID des Buchungsbetriebs
     */
    void deactivateBusiness(String businessId);
}


