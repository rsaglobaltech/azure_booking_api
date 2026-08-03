package com.booking.azure.domain.exception;

/**
 * Microsoft Graph hat <b>nicht</b> geantwortet: Zeitüberschreitung, abgebrochene
 * Verbindung, Netzfehler.
 *
 * <h2>Der entscheidende Unterschied</h2>
 *
 * Eine Zeitüberschreitung bedeutet <b>nicht</b>, dass Graph die Anfrage abgelehnt
 * hat. Sie bedeutet, dass wir das Ergebnis nicht kennen. Der Termin kann sehr
 * wohl angelegt worden sein – die Antwort hat uns nur nicht mehr erreicht.
 *
 * <h2>Konsequenz für die Slot-Reservierung</h2>
 *
 * Die Reservierung darf in diesem Fall <b>nicht</b> freigegeben werden. Eine
 * Freigabe ohne Gewissheit erzeugt genau die Doppelbuchung, die sie verhindern
 * soll:
 *
 * <pre>
 *   t=0    Anfrage A reserviert, sendet POST. Graph antwortet nicht.
 *   t=30   Zeitüberschreitung. Slot würde freigegeben.
 *   t=31   Anfrage B (Wiederholung) belegt den Slot, POST → 201
 *   t=35   Der POST von A erreicht Graph doch noch → 201
 *          Zwei überlappende Termine.
 * </pre>
 *
 * Der Slot bleibt daher {@code PENDING}, bis der Wiederherstellungsjob über
 * {@code GET /calendarView} <em>geprüft</em> hat, ob der Termin entstanden ist.
 * Siehe docs/PLAN-COLISION-RESERVAS.md §2.5.
 *
 * Gegenstück: {@link GraphResponseException}.
 */
public class GraphUnknownException extends RuntimeException {

    public GraphUnknownException(String nachricht, Throwable cause) {
        super(nachricht, cause);
    }
}


