package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.domain.command.CreateServiceRequest;

import java.util.List;

/**
 * Eingehender Port (Use-Case-Interface) für die Dienstverwaltung.
 *
 * Onion-Architektur – Domänenschicht:
 *   Definiert alle Use Cases rund um Dienstleistungen (Services),
 *   die ein Buchungsbetrieb anbietet. Kunden können über die Buchungs-URL
 *
 *   <pre>https://outlook.office.com/book/{agenturName}@midominio.com</pre>
 *
 *   einen dieser Dienste buchen. Jeder Dienst hat eine konfigurierbare
 *   Dauer, einen Preis und kann bestimmten Mitarbeitern zugewiesen werden.
 */
public interface ServiceManagement {

    /**
     * Alle Dienste eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID des Buchungsbetriebs
     * @return Liste aller verfügbaren Dienste
     */
    List<BookingServiceDto> listServices(String businessId);

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param serviceId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    BookingServiceDto getService(String businessId, String serviceId);

    /**
     * Einen neuen Dienst in einem Buchungsbetrieb erstellen.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param request   Anfrage mit Dienstname, Dauer, Preis und zugewiesenen Mitarbeitern
     * @return Der erstellte Dienst
     */
    BookingServiceDto createService(String businessId, CreateServiceRequest request);

    /**
     * Einen bestehenden Dienst aktualisieren.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param serviceId  ID des zu aktualisierenden Dienstes
     * @param request   Anfrage mit den neuen Dienstdaten
     * @return Der aktualisierte Dienst
     */
    BookingServiceDto updateService(String businessId,
                                          String serviceId,
                                          CreateServiceRequest request);

    /**
     * Einen Dienst aus einem Buchungsbetrieb löschen.
     *
     * @param businessId ID des Buchungsbetriebs
     * @param serviceId  ID des zu löschenden Dienstes
     */
    void deleteService(String businessId, String serviceId);
}


