ALTER TABLE booking_items
    ADD CONSTRAINT chk_booking_item_type
        CHECK (
            (seat_id IS NOT NULL AND ga_area_id IS NULL AND table_id IS NULL) OR
            (ga_area_id IS NOT NULL AND seat_id IS NULL AND table_id IS NULL) OR
            (table_id IS NOT NULL AND seat_id IS NULL AND ga_area_id IS NULL)
            );