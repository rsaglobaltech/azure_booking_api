package com.booking.azure.domain.port.out;

import com.booking.azure.domain.exception.SlotConflictException;
import com.booking.azure.domain.model.SlotAnfrage;
import com.booking.azure.domain.model.VerwaisteReservierung;

import java.time.Instant;
import java.util.List;

/**
 * Ausgehender Port für die atomare Reservierung von Terminfenstern.
 *
 * Onion-Architektur – Domänenschicht:
 *   Definiert den Vertrag; die Implementierung
 *   ({@code SlotReservationJpaAdapter}) liegt in der Infrastrukturschicht.
 *
 * <h2>Warum dieser Port existiert</h2>
 *
 * Microsoft Graph kann nicht die maßgebliche Instanz für gegenseitigen
 * Ausschluss sein: es bietet weder Sperren noch Transaktionen noch bedingte
 * Schreibvorgänge auf {@code bookingAppointment}. Der verwendete Endpunkt ist
 * zudem der administrative, der Überbuchung bewusst erlaubt.
 *
 * Dieser Port kapselt daher den eigenen Zustand, der die Entscheidung trifft.
 *
 * <h2>Vorgesehene Aufrufreihenfolge</h2>
 *
 * <pre>
 *   1. reservieren(...)            → eigene Transaktion, wird sofort festgeschrieben
 *   2. POST an Microsoft Graph     → außerhalb jeder Transaktion
 *   3a. bestaetigen(...)           → bei Erfolg
 *   3b. freigeben(...)             → bei Fehler (Kompensation)
 * </pre>
 *
 * <p><b>Schritt 2 darf nicht innerhalb einer offenen Transaktion laufen.</b>
 * Eine Transaktion über einen Netzaufruf hinweg offen zu halten erschöpft unter
 * Last den Verbindungspool.
 */
public interface SlotReservierung {

    /**
     * Reserviert das Zeitfenster für alle angefragten Mitarbeiter – ganz oder gar nicht.
     *
     * @param anfrage Zeitfenster in UTC, Betrieb, Dienst und Mitarbeiter
     * @return Undurchsichtige Kennungen der angelegten Reservierungen,
     *         weiterzureichen an {@link #bestaetigen} oder {@link #freigeben}
     * @throws SlotConflictException wenn sich das Fenster für mindestens einen
     *         Mitarbeiter mit einer bestehenden Reservierung überschneidet
     */
    List<Long> reservieren(SlotAnfrage anfrage);

    /**
     * Markiert die Reservierungen als bestätigt und verknüpft sie mit dem in
     * Graph angelegten Termin.
     *
     * @param reservierungsIds Rückgabewert von {@link #reservieren}
     * @param graphTerminId    ID des Termins aus der Graph-Antwort
     */
    void bestaetigen(List<Long> reservierungsIds, String graphTerminId);

    /**
     * Gibt die Reservierungen wieder frei (Kompensation nach fehlgeschlagenem
     * Graph-Aufruf). Der Slot wird dadurch erneut buchbar.
     *
     * @param reservierungsIds Rückgabewert von {@link #reservieren}
     */
    void freigeben(List<Long> reservierungsIds);

    /**
     * Gibt alle Reservierungen zu einem Graph-Termin frei.
     *
     * Wird bei Stornierung aufgerufen. Ohne diesen Schritt bliebe der Slot
     * dauerhaft blockiert, obwohl der Termin in Graph nicht mehr existiert.
     *
     * @param graphTerminId ID des stornierten Termins
     * @return Anzahl der freigegebenen Reservierungen
     */
    int freigebenNachTerminId(String graphTerminId);

    /**
     * Bucht einen bestehenden Termin auf ein neues Zeitfenster um: gibt die
     * alten Reservierungen frei und belegt die neuen – in <b>einer</b> Transaktion.
     *
     * Zwei getrennte Aufrufe wären fehlerhaft: zwischen Freigabe und Neubelegung
     * könnte eine andere Anfrage den alten Slot übernehmen, und bei einem Fehler
     * der Neubelegung wäre der alte Slot bereits verloren.
     *
     * @param graphTerminId ID des umzubuchenden Termins
     * @param neuesFenster  neues Zeitfenster in UTC
     * @return Kennungen der neuen Reservierungen
     * @throws SlotConflictException wenn das neue Fenster belegt ist
     */
    List<Long> umbuchen(String graphTerminId, SlotAnfrage neuesFenster);

    /**
     * Findet {@code PENDING}-Reservierungen, deren Frist abgelaufen ist.
     *
     * <p><b>Diese Zeilen dürfen nicht blind freigegeben werden.</b> Das Ablaufen
     * einer Frist bricht keinen laufenden HTTP-Aufruf ab und beweist nicht, dass
     * der Termin fehlt. Eine Freigabe ohne vorherige Prüfung bei Graph erzeugt
     * genau die Doppelbuchung, die das Verfahren verhindern soll.
     *
     * @param vor Stichzeitpunkt; geliefert werden Zeilen mit {@code expires_at} davor
     * @return verwaiste Reservierungen, zur Prüfung gegen {@code GET /calendarView}
     */
    List<VerwaisteReservierung> verwaisteFinden(Instant vor);
}
