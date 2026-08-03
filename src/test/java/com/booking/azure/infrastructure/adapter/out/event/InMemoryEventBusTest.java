package com.booking.azure.infrastructure.adapter.out.event;

import com.booking.azure.domain.event.BaseDomainEvent;
import com.booking.azure.domain.event.DomainEvent;
import com.booking.azure.domain.event.DomainEventHandler;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routing and failure isolation of the in-memory bus, without a Spring context.
 */
public class InMemoryEventBusTest {

    // ─────────────────────────── test doubles ───────────────────────────

    static final class Alpha extends BaseDomainEvent {
        Alpha() {
            super("aggregate-1");
        }
    }

    static final class Beta extends BaseDomainEvent {
        Beta() {
            super("aggregate-2");
        }
    }

    static final class Recorder<T extends DomainEvent> implements DomainEventHandler<T> {
        private final Class<T> type;
        final List<T> received = new ArrayList<>();

        Recorder(Class<T> type) {
            this.type = type;
        }

        @Override
        public Class<T> eventType() {
            return type;
        }

        @Override
        public void handle(T event) {
            received.add(event);
        }
    }

    static final class Exploding implements DomainEventHandler<Alpha> {
        @Override
        public Class<Alpha> eventType() {
            return Alpha.class;
        }

        @Override
        public void handle(Alpha event) {
            throw new IllegalStateException("mail server unreachable");
        }
    }

    // ───────────────────────────── the tests ─────────────────────────────

    @Test(description = "An event reaches only the handlers registered for its type")
    public void routesByEventType() {
        Recorder<Alpha> alpha = new Recorder<>(Alpha.class);
        Recorder<Beta> beta = new Recorder<>(Beta.class);
        InMemoryEventBus bus = new InMemoryEventBus(List.of(alpha, beta));

        bus.publish(new Alpha());

        assertThat(alpha.received).hasSize(1);
        assertThat(beta.received).isEmpty();
    }

    @Test(description = "Every handler subscribed to a type receives the event")
    public void deliversToEverySubscriber() {
        Recorder<Alpha> first = new Recorder<>(Alpha.class);
        Recorder<Alpha> second = new Recorder<>(Alpha.class);
        InMemoryEventBus bus = new InMemoryEventBus(List.of(first, second));

        bus.publish(new Alpha());

        assertThat(first.received).hasSize(1);
        assertThat(second.received).hasSize(1);
    }

    @Test(description = "A handler that throws is contained: the others still run and "
            + "the publisher never sees the failure")
    public void aFailingHandlerDoesNotStopTheOthers() {
        Recorder<Alpha> survivor = new Recorder<>(Alpha.class);
        InMemoryEventBus bus = new InMemoryEventBus(List.of(new Exploding(), survivor));

        bus.publish(new Alpha());

        assertThat(survivor.received)
                .describedAs("a failing mail server must not take down a confirmed booking")
                .hasSize(1);
    }

    @Test(description = "An event nobody subscribed to is simply dropped")
    public void unsubscribedEventIsHarmless() {
        InMemoryEventBus bus = new InMemoryEventBus(List.of(new Recorder<>(Beta.class)));

        bus.publish(new Alpha());
    }

    @Test(description = "publishAll delivers each event in turn")
    public void publishAllDeliversEveryEvent() {
        Recorder<Alpha> alpha = new Recorder<>(Alpha.class);
        Recorder<Beta> beta = new Recorder<>(Beta.class);
        InMemoryEventBus bus = new InMemoryEventBus(List.of(alpha, beta));

        bus.publishAll(List.of(new Alpha(), new Beta(), new Alpha()));

        assertThat(alpha.received).hasSize(2);
        assertThat(beta.received).hasSize(1);
    }

    @Test(description = "An empty batch is a no-op")
    public void publishAllToleratesAnEmptyBatch() {
        InMemoryEventBus bus = new InMemoryEventBus(List.of());
        bus.publishAll(List.of());
    }
}
