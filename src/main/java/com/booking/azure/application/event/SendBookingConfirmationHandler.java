package com.booking.azure.application.event;

import com.booking.azure.domain.event.BookingConfirmed;
import com.booking.azure.domain.event.DomainEventHandler;
import com.booking.azure.domain.model.Agency;
import com.booking.azure.domain.model.BookingDetails;
import com.booking.azure.domain.model.vo.CustomerContact;
import com.booking.azure.domain.port.BookingNotificationPort;
import com.booking.azure.domain.port.out.AgencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Sends the confirmation emails when a booking is confirmed.
 *
 * <h2>What this replaces</h2>
 *
 * The appointment use case used to build the notification inline and call the
 * mail port itself, inside a {@code try/catch} that swallowed every failure.
 * Booking logic therefore had to know that emails exist and defend itself
 * against them. Now it states that a booking was confirmed, and this handler
 * decides that a confirmation is worth an email.
 *
 * <p>Failure isolation lives in the bus, not here: a handler that throws is
 * logged and skipped, so this class no longer needs a defensive catch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendBookingConfirmationHandler implements DomainEventHandler<BookingConfirmed> {

    private final BookingNotificationPort notificationPort;
    private final AgencyRepository agencyRepository;

    @Override
    public Class<BookingConfirmed> eventType() {
        return BookingConfirmed.class;
    }

    @Override
    public void handle(BookingConfirmed event) {
        Optional<CustomerContact> customer = event.customer();
        if (customer.isEmpty()) {
            // A booking confirmed by the recovery job carries no customer: it was
            // rebuilt from stored reservation rows. Nothing to write to, and the
            // original confirmation email was already sent on the create path.
            log.debug("Booking {} confirmed without customer details, no email sent",
                    event.bookingId());
            return;
        }

        BookingDetails details = BookingDetails.builder()
                .agencyName(agencyName(event).orElse(event.businessId().value()))
                .agencyEmail(event.businessId().value())
                .customerName(customer.get().name())
                .customerEmail(customer.get().email())
                .workerName(event.staffMemberIds().isEmpty()
                        ? "" : event.staffMemberIds().get(0).value())
                .serviceName(event.serviceId().value())
                .startTime(event.window().start().atOffset(ZoneOffset.UTC))
                .endTime(event.window().end().atOffset(ZoneOffset.UTC))
                .build();

        notificationPort.notifyBookingConfirmed(details);
    }

    /**
     * The event carries the Microsoft business id; the email should show the
     * name people recognise. Falls back to the id if the agency cannot be read —
     * a missing display name is no reason to skip the confirmation.
     */
    private Optional<String> agencyName(BookingConfirmed event) {
        try {
            return agencyRepository.findByBusinessId(event.businessId())
                    .map(Agency::name)
                    .map(name -> name.value());
        } catch (RuntimeException ex) {
            log.warn("Could not resolve agency name for {}: {}", event.businessId(), ex.getMessage());
            return Optional.empty();
        }
    }
}
