package com.booking.azure.domain.port.out;

/**
 * Ausgehender Port (Sekundär-Adapter-Interface) für HTTP-Anfragen
 * an die Microsoft Graph API.
 *
 * Onion-Architektur – Domänenschicht:
 *   Dieser Port definiert den Vertrag, über den die Anwendungsschicht
 *   (Application Services) externe Systeme (Microsoft Graph) aufruft.
 *   Die konkrete Implementierung liegt in der Infrastrukturschicht
 *   ({@code GraphApiClient}).
 *
 *   Abhängigkeitsregel (Dependency Rule):
 *     Domäne ← Anwendung ← Infrastruktur
 *   Die Infrastrukturschicht implementiert diesen Port; die Domänenschicht
 *   kennt die Infrastruktur nicht.
 *
 * Authentifizierung:
 *   Jede Anfrage erhält automatisch einen Bearer-Token via
 *   OAuth 2.0 Client-Credentials-Flow (Azure AD).
 */
public interface GraphApiRequest {

    /**
     * HTTP-GET-Anfrage an die Graph API senden.
     *
     * @param path       Relativer API-Pfad (ohne Base-URL),
     *                   z. B. {@code /solutions/bookingBusinesses}
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T get(String path, Class<T> antwortTyp);

    /**
     * HTTP-POST-Anfrage an die Graph API senden.
     *
     * @param path       Relativer API-Pfad
     * @param body    Anfrageobjekt (wird als JSON serialisiert)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T post(String path, Object body, Class<T> antwortTyp);

    /**
     * HTTP-PATCH-Anfrage an die Graph API senden (Teilaktualisierung).
     *
     * @param path       Relativer API-Pfad
     * @param body    Anfrageobjekt mit den zu ändernden Feldern
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T patch(String path, Object body, Class<T> antwortTyp);

    /**
     * HTTP-DELETE-Anfrage an die Graph API senden.
     *
     * @param path Relativer API-Pfad der zu löschenden Ressource
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    void delete(String path);
}


