package com.booking.azure.infrastructure.adapter.out.event;

import com.booking.azure.domain.event.DomainEvent;
import com.booking.azure.domain.event.DomainEventHandler;
import com.booking.azure.domain.port.out.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure adapter: delivers domain events to in-process handlers.
 *
 * <h2>Why a registry of its own instead of Spring's ApplicationEventPublisher</h2>
 *
 * Wrapping Spring's publisher would have been shorter, but it drags the
 * framework into the shape of the domain: handlers become
 * {@code @EventListener}-annotated Spring beans, events end up needing to
 * satisfy Spring's contracts, and testing a reaction requires a context. Here
 * the domain declares {@link DomainEventHandler} and this adapter is the only
 * thing that knows Spring exists. Swapping in a real broker means writing
 * another adapter and touching nothing above it.
 *
 * <h2>Delivery semantics</h2>
 *
 * Synchronous, in the publishing thread, in registration order. <b>A handler
 * that throws is logged and skipped</b>: emails are a reaction to a booking, not
 * part of it, and a failing mail server must not roll back a confirmed
 * appointment.
 *
 * <h2>What this does not give you</h2>
 *
 * Events live in memory only. If the process dies between the commit and the
 * dispatch, the event is gone — no retry, no replay. Accepted deliberately
 * while notifications are the only subscriber; a transactional outbox is the
 * answer once anything load-bearing listens. See docs/PLAN-DDD.md §5.
 */
@Slf4j
@Component
public class InMemoryEventBus implements DomainEventPublisher {

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> handlers;

    public InMemoryEventBus(List<DomainEventHandler<? extends DomainEvent>> discovered) {
        Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> registry =
                new HashMap<>();

        for (DomainEventHandler<? extends DomainEvent> handler : discovered) {
            registry.computeIfAbsent(handler.eventType(), type -> new java.util.ArrayList<>())
                    .add(handler);
        }

        this.handlers = Map.copyOf(registry);
        log.info("Domain event bus ready: {} event type(s) subscribed", handlers.size());
    }

    @Override
    public void publish(DomainEvent event) {
        List<DomainEventHandler<? extends DomainEvent>> subscribers =
                handlers.getOrDefault(event.getClass(), List.of());

        if (subscribers.isEmpty()) {
            log.debug("No subscriber for {}", event);
            return;
        }

        for (DomainEventHandler<? extends DomainEvent> subscriber : subscribers) {
            dispatch(subscriber, event);
        }
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

    /**
     * The cast is safe because the registry is keyed by the very type the
     * handler declares, and the event is looked up by its own class.
     */
    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> void dispatch(DomainEventHandler<T> handler, DomainEvent event) {
        try {
            handler.handle((T) event);
        } catch (RuntimeException ex) {
            // Isolated on purpose: one broken reaction must not take down the
            // others, nor the business operation that already succeeded.
            log.error("Handler {} failed on {}: {}",
                    handler.getClass().getSimpleName(), event, ex.getMessage(), ex);
        }
    }
}
