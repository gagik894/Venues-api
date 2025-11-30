-- Add sales channel tracking to bookings table
-- This allows tracking where each booking originated: website, direct sale, or platform

-- Add sales_channel column (nullable initially for backfill)
ALTER TABLE bookings
    ADD COLUMN sales_channel VARCHAR(20);

-- Add staff_id to track which staff member made direct sales
ALTER TABLE bookings
    ADD COLUMN staff_id UUID;

-- Backfill existing bookings with sales channel
-- If platform_id is set, it's a PLATFORM sale
-- Otherwise, assume it's a WEBSITE sale (customer online purchase)
UPDATE bookings
SET sales_channel = CASE
                        WHEN platform_id IS NOT NULL THEN 'PLATFORM'
                        ELSE 'WEBSITE'
    END;

-- Make sales_channel required now that all rows have values
ALTER TABLE bookings
    ALTER COLUMN sales_channel SET NOT NULL;

-- Add indexes for query performance
CREATE INDEX idx_booking_sales_channel ON bookings (sales_channel);
CREATE INDEX idx_booking_staff_id ON bookings (staff_id) WHERE staff_id IS NOT NULL;
CREATE INDEX idx_booking_channel_status ON bookings (sales_channel, status);

-- Add check constraints for data integrity
ALTER TABLE bookings
    ADD CONSTRAINT chk_platform_sales_have_platform_id
        CHECK (sales_channel != 'PLATFORM' OR platform_id IS NOT NULL);

ALTER TABLE bookings
    ADD CONSTRAINT chk_direct_sales_have_staff_id
        CHECK (sales_channel != 'DIRECT_SALE' OR staff_id IS NOT NULL);
