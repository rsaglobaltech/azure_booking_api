package com.booking.azure.application.service;

import com.booking.azure.application.port.in.ServiceManagement;
import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.dto.BookingServiceDto;
import com.booking.azure.application.dto.ListResponse;
import com.booking.azure.application.command.CreateServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anwendungsdienst für die Verwaltung von Buchungsdienstleistungen.
 *
 * Onion-Architektur – Anwendungsschicht:
 *   Implementiert den eingehenden Port {@link ServiceManagement}.
 *   Abhängig vom ausgehenden Port {@link GraphApiRequest}.
 *
 * Jeder Dienst gehört zu einem Buchungsbetrieb (Agentur) und
 * kann von Kunden über die öffentliche Buchungs-URL gebucht werden:
 *   https://outlook.office.com/book/{agenturName}@midominio.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceMgr implements ServiceManagement {

    /** Ausgehender Port – wird durch den GraphApiClient implementiert */
    private final GraphApiRequest graphApiRequest;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String dienstePfad(String businessId) {
        return "/solutions/bookingBusinesses/" + businessId + "/services";
    }

    /**
     * Alle Dienstleistungen eines Buchungsbetriebs auflisten.
     *
     * @param businessId ID der Agentur
     * @return Liste aller Dienste
     */
    @Override
    public List<BookingServiceDto> listServices(String businessId) {
        log.info("Dienste werden aufgelistet für Betrieb: {}", businessId);
        ListResponse<BookingServiceDto> response = graphApiRequest.get(
                dienstePfad(businessId), ListResponse.class);
        return listeMappen(response.getValue(), BookingServiceDto.class);
    }

    /**
     * Einen bestimmten Dienst abrufen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID (GUID) des Dienstes
     * @return Dienstdaten
     */
    @Override
    public BookingServiceDto getService(String businessId, String serviceId) {
        log.info("Dienst {} wird abgerufen für Betrieb {}", serviceId, businessId);
        return graphApiRequest.get(dienstePfad(businessId) + "/" + serviceId, BookingServiceDto.class);
    }

    /**
     * Neuen Dienst in einem Buchungsbetrieb erstellen.
     *
     * @param businessId ID der Agentur
     * @param request   Dienstdaten (Name, Dauer, Preis, zugewiesene Mitarbeiter)
     * @return Der erstellte Dienst
     */
    @Override
    public BookingServiceDto createService(String businessId, CreateServiceRequest request) {
        log.info("Neuer Dienst '{}' wird erstellt in Betrieb {}", request.getDisplayName(), businessId);
        return graphApiRequest.post(dienstePfad(businessId), request, BookingServiceDto.class);
    }

    /**
     * Bestehenden Dienst aktualisieren.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des Dienstes
     * @param request   Neue Dienstdaten
     * @return Der aktualisierte Dienst
     */
    @Override
    public BookingServiceDto updateService(String businessId,
                                                 String serviceId,
                                                 CreateServiceRequest request) {
        log.info("Dienst {} wird aktualisiert in Betrieb {}", serviceId, businessId);
        return graphApiRequest.patch(dienstePfad(businessId) + "/" + serviceId,
                request, BookingServiceDto.class);
    }

    /**
     * Dienst aus einem Buchungsbetrieb löschen.
     *
     * @param businessId ID der Agentur
     * @param serviceId  ID des zu löschenden Dienstes
     */
    @Override
    public void deleteService(String businessId, String serviceId) {
        log.info("Dienst {} wird gelöscht in Betrieb {}", serviceId, businessId);
        graphApiRequest.delete(dienstePfad(businessId) + "/" + serviceId);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> listeMappen(List<?> rohliste, Class<T> zielklasse) {
        if (rohliste == null) return List.of();
        return objectMapper.convertValue(rohliste,
                objectMapper.getTypeFactory().constructCollectionType(List.class, zielklasse));
    }
}


