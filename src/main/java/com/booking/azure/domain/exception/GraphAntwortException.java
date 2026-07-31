package com.booking.azure.domain.exception;

/**
 * Microsoft Graph hat geantwortet – mit einem Fehlerstatus.
 *
 * <h2>Warum diese Unterscheidung existiert</h2>
 *
 * Eine empfangene Fehlerantwort ist eine <b>Gewissheit</b>: Graph hat die
 * Anfrage verarbeitet und abgelehnt. Es wurde kein Termin angelegt.
 *
 * Damit ist es sicher, eine zuvor belegte Slot-Reservierung wieder freizugeben.
 *
 * Gegenstück: {@link GraphUnbekanntException}, bei der genau das nicht gilt.
 */
public class GraphAntwortException extends RuntimeException {

    /** HTTP-Status der Graph-Antwort. */
    private final int status;

    public GraphAntwortException(int status, String nachricht) {
        super(nachricht);
        this.status = status;
    }

    public GraphAntwortException(int status, String nachricht, Throwable ursache) {
        super(nachricht, ursache);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
