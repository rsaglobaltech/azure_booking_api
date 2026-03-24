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
public interface GraphApiAnfrage {

    /**
     * HTTP-GET-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad (ohne Base-URL),
     *                   z. B. {@code /solutions/bookingBusinesses}
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T get(String pfad, Class<T> antwortTyp);

    /**
     * HTTP-POST-Anfrage an die Graph API senden.
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt (wird als JSON serialisiert)
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T post(String pfad, Object koerper, Class<T> antwortTyp);

    /**
     * HTTP-PATCH-Anfrage an die Graph API senden (Teilaktualisierung).
     *
     * @param pfad       Relativer API-Pfad
     * @param koerper    Anfrageobjekt mit den zu ändernden Feldern
     * @param antwortTyp Zielklasse für die JSON-Deserialisierung
     * @param <T>        Erwarteter Rückgabetyp
     * @return Deserialisiertes Antwortobjekt
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    <T> T patch(String pfad, Object koerper, Class<T> antwortTyp);

    /**
     * HTTP-DELETE-Anfrage an die Graph API senden.
     *
     * @param pfad Relativer API-Pfad der zu löschenden Ressource
     * @throws RuntimeException bei HTTP-Fehlern der Graph API
     */
    void delete(String pfad);
}
