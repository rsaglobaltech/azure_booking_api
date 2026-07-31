package com.booking.azure.domain.port;

import com.booking.azure.domain.model.BookingDetails;

public interface BookingNotificationPort {
    void notifyBookingConfirmed(BookingDetails details);
}


