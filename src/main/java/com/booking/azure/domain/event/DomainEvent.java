package com.booking.azure.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in the domain, stated in the past tense.
 *
 * <h2>Why events instead of direct calls</h2>
 *
 * Confirming a booking used to call the notification port inline, wrapped in a
 * {@code try/catch} that swallowed every failure — the use case both knew that
 * emails exist and had to defend itself against them. An event inverts that:
 * the aggregate states what happened, and whoever cares subscribes. Adding a
 * second reaction no longer means editing the booking logic.
 *
 * <p>No Spring, no Jackson, no persistence annotations: an event is a domain
 * fact, and the transport that carries it is an infrastructure choice.
 */
public interface DomainEvent {

    /** Identity of this occurrence, distinct from the aggregate it describes. */
    UUID eventId();

    /** When the fact became true. */
    Instant occurredOn();

    /** Identity of the aggregate the fact is about. */
    String aggregateId();
}
