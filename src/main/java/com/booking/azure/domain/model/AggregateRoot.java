package com.booking.azure.domain.model;

import com.booking.azure.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregate roots that record what happened to them.
 *
 * <h2>Why the aggregate collects rather than publishes</h2>
 *
 * An aggregate that published directly would need a publisher handed to it, and
 * would announce facts before they were durable — a confirmation that then
 * failed to commit. Instead it records events as it changes, and the caller
 * drains them once the write has succeeded.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Returns the recorded events and clears them.
     *
     * <p>Draining rather than reading: an event delivered twice would send the
     * customer a second confirmation email for a booking that was confirmed
     * once.
     */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }

    public boolean hasEvents() {
        return !domainEvents.isEmpty();
    }
}
