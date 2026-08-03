-- ============================================================================
-- Booking aggregate identity
-- ============================================================================
--
-- Reservation rows created by the same booking had no way of referring to one
-- another: they only shared business, service, window and creation instant.
-- Reconstructing the booking from those columns would have been guesswork, and
-- without an aggregate identity there is nothing for a domain event to point at.
--
-- Additive migration: the column is added nullable, existing rows are backfilled
-- one identity each (legacy rows cannot be grouped after the fact), and only
-- then does it become mandatory.
-- ============================================================================

ALTER TABLE slot_reservation ADD (booking_id VARCHAR2(36));

UPDATE slot_reservation
   SET booking_id = LOWER(RAWTOHEX(SYS_GUID()))
 WHERE booking_id IS NULL;

ALTER TABLE slot_reservation MODIFY (booking_id VARCHAR2(36) NOT NULL);

CREATE INDEX ix_slot_booking ON slot_reservation (booking_id);
