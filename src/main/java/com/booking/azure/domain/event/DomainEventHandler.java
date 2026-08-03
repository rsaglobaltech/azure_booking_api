package com.booking.azure.domain.event;

/**
 * A reaction to one kind of domain event.
 *
 * Implementations live in the application layer and are discovered by the event
 * bus at startup. {@link #eventType()} is declared explicitly rather than read
 * from the generic parameter because generics are erased at runtime and the bus
 * has to route by actual class.
 *
 * @param <T> the event this handler reacts to
 */
public interface DomainEventHandler<T extends DomainEvent> {

    Class<T> eventType();

    void handle(T event);
}
