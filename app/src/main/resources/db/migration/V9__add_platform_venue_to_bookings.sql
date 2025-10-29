-- ================================================================
-- Venues API - Booking Module Enhancement
-- Version: 9
-- Description: Add platform_id and venue_id to bookings table
-- ================================================================

-- Add platform_id column
ALTER TABLE bookings
    ADD COLUMN platform_id BIGINT;

-- Add venue_id column
ALTER TABLE bookings
    ADD COLUMN venue_id BIGINT;

-- Create indexes for efficient querying
CREATE INDEX idx_booking_platform_id ON bookings (platform_id);
CREATE INDEX idx_booking_venue_id ON bookings (venue_id);

-- Add foreign key constraint to platforms table
ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_platform
        FOREIGN KEY (platform_id) REFERENCES platforms (id) ON DELETE SET NULL;

-- Add foreign key constraint to venues table
ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE SET NULL;

-- Add comments
COMMENT
ON COLUMN bookings.platform_id IS 'Platform ID if booking was made through external platform integration';
COMMENT
ON COLUMN bookings.venue_id IS 'Venue ID where the event is held (denormalized for reporting)';

