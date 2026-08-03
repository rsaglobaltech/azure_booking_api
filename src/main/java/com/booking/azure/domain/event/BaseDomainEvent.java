package com.booking.azure.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Carries the identity and timestamp every {@link DomainEvent} needs, so
 * concrete events only declare what makes them different.
 */
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredOn = Instant.now();
    private final String aggregateId;

    protected BaseDomainEvent(String aggregateId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        this.aggregateId = aggregateId;
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }

    @Override
    public String aggregateId() {
        return aggregateId;
    }

    @Override
    public String toString() {
        return "%s(aggregate=%s, at=%s)".formatted(getClass().getSimpleName(), aggregateId, occurredOn);
    }
}
