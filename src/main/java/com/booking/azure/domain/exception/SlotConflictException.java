package com.booking.azure.domain.exception;

/**
 * Der angefragte Zeitraum ist für mindestens einen der Mitarbeiter bereits belegt.
 *
 * Onion-Architektur – Domänenschicht: fachliche Ausnahme, unabhängig von HTTP.
 * Die Abbildung auf {@code HTTP 409 Conflict} geschieht im
 * {@code GlobalExceptionHandler} der Präsentationsschicht.
 *
 * <p>Wird ausgelöst, wenn die {@code EXCLUDE}-Bedingung {@code ex_slot_overlap}
 * den Einfügeversuch abweist – also genau dann, wenn eine gleichzeitige oder
 * frühere Anfrage den Slot bereits gewonnen hat.
 */
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String nachricht) {
        super(nachricht);
    }

    public SlotConflictException(String nachricht, Throwable ursache) {
        super(nachricht, ursache);
    }
}
