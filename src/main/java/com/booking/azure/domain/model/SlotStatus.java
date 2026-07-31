package com.booking.azure.domain.model;

/**
 * Lebenszyklus einer Slot-Reservierung.
 *
 * Nur {@link #PENDING} und {@link #CONFIRMED} blockieren den Slot – so ist es
 * in der {@code EXCLUDE}-Bedingung {@code ex_slot_overlap} hinterlegt.
 */
public enum SlotStatus {

    /**
     * Slot belegt, Termin in Graph noch nicht bestätigt.
     * Zustand zwischen dem Festschreiben der Reservierung und der Antwort von Graph.
     */
    PENDING,

    /** Termin in Graph angelegt, {@code graph_appointment_id} gesetzt. */
    CONFIRMED,

    /**
     * Slot wieder freigegeben – nach fehlgeschlagenem Graph-Aufruf, Stornierung
     * oder Umbuchung. Die Zeile bleibt als Prüfspur erhalten und blockiert nicht mehr.
     */
    RELEASED
}
