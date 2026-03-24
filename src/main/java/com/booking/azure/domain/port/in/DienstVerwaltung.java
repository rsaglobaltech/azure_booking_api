package com.booking.azure.domain.port.in;

import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.dto.request.CreateServiceRequest;

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
public interface DienstVerwaltung {

    /**
     * Alle Dienste eines Buchungsbetriebs auflisten.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @return Liste aller verfügbaren Dienste
     */
    List<BookingServiceDto> diensteAuflisten(String betriebId);

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param dienstId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    BookingServiceDto dienstAbrufen(String betriebId, String dienstId);

    /**
     * Einen neuen Dienst in einem Buchungsbetrieb erstellen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param anfrage   Anfrage mit Dienstname, Dauer, Preis und zugewiesenen Mitarbeitern
     * @return Der erstellte Dienst
     */
    BookingServiceDto dienstErstellen(String betriebId, CreateServiceRequest anfrage);

    /**
     * Einen bestehenden Dienst aktualisieren.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param dienstId  ID des zu aktualisierenden Dienstes
     * @param anfrage   Anfrage mit den neuen Dienstdaten
     * @return Der aktualisierte Dienst
     */
    BookingServiceDto dienstAktualisieren(String betriebId,
                                          String dienstId,
                                          CreateServiceRequest anfrage);

    /**
     * Einen Dienst aus einem Buchungsbetrieb löschen.
     *
     * @param betriebId ID des Buchungsbetriebs
     * @param dienstId  ID des zu löschenden Dienstes
     */
    void dienstLoeschen(String betriebId, String dienstId);
}
