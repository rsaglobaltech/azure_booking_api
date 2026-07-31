package com.booking.azure.infrastructure.adapter.out.notification;

import com.booking.azure.domain.model.BookingDetails;
import com.booking.azure.domain.port.BookingNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationAdapter implements BookingNotificationPort {

    private final JavaMailSender mailSender;

    @Autowired
    public EmailNotificationAdapter(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async
    public void notifyBookingConfirmed(BookingDetails details) {
        log.info("Starting email notification process for booking...");
        
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Falling back to console logging.");
            logConsoleDummy(details);
            return;
        }

        try {
            // Send to Customer
            SimpleMailMessage customerMsg = new SimpleMailMessage();
            customerMsg.setTo(details.getCustomerEmail());
            customerMsg.setSubject("Booking Confirmed: " + details.getServiceName());
            customerMsg.setText(String.format("Dear %s,\n\nYour booking for %s at %s with %s is confirmed for %s.\n\nThank you!",
                    details.getCustomerName(), details.getServiceName(), details.getAgencyName(),
                    details.getWorkerName(), details.getStartTime()));
            mailSender.send(customerMsg);
            log.info("Confirmation email sent to customer: {}", details.getCustomerEmail());

            // Send to Agency/Worker
            SimpleMailMessage agencyMsg = new SimpleMailMessage();
            agencyMsg.setTo(details.getAgencyEmail());
            agencyMsg.setSubject("New Booking: " + details.getServiceName());
            agencyMsg.setText(String.format("New booking confirmed!\n\nCustomer: %s\nTime: %s\nService: %s",
                    details.getCustomerName(), details.getStartTime(), details.getServiceName()));
            mailSender.send(agencyMsg);
            log.info("Notification email sent to agency: {}", details.getAgencyEmail());

        } catch (Exception e) {
            log.error("Failed to send email notification", e);
        }
    }

    private void logConsoleDummy(BookingDetails details) {
        log.info("=====================================================");
        log.info("ASYNC NOTIFICATION SIMULATION (Ports & Adapters)");
        log.info("Sending Email to Customer: {}", details.getCustomerEmail());
        log.info("Dear {}, your booking for {} at {} with {} is confirmed.", 
                 details.getCustomerName(), details.getServiceName(), 
                 details.getAgencyName(), details.getWorkerName());
        
        log.info("Sending Email to Agency: {}", details.getAgencyEmail());
        log.info("New booking confirmed! Customer: {}, Time: {}", 
                 details.getCustomerName(), details.getStartTime());
        log.info("=====================================================");
    }
}
