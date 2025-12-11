-- Add SELLER venue role support
ALTER TABLE staff_venue_permissions
    DROP CONSTRAINT IF EXISTS staff_venue_permissions_role_check;

ALTER TABLE staff_venue_permissions
    ADD CONSTRAINT staff_venue_permissions_role_check
        CHECK (role IN ('VIEWER', 'SCANNER', 'EDITOR', 'MANAGER', 'SELLER'));

