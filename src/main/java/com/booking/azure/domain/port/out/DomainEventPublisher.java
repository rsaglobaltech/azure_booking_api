package com.booking.azure.domain.port.out;

import com.booking.azure.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbound port for handing domain events to whatever carries them.
 *
 * The implementation is currently an in-memory bus. Replacing it with a real
 * broker means writing another adapter and changing nothing above this line —
 * which is the whole reason the port exists.
 *
 * <h2>When to publish</h2>
 *
 * <b>After the aggregate has been saved</b>, never inside the transaction that
 * saves it. An event announcing a confirmation that then failed to commit is a
 * lie that subscribers have already acted on.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    void publishAll(List<DomainEvent> events);
}
