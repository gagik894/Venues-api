/*
 * Flyway Migration V12
 * Description: Add platform_id to carts table for platform reservation ownership tracking
 *
 * Context: Security audit findings CRIT-01 and CRIT-02 revealed that platforms could
 * steal other platforms' reservations by guessing/intercepting cart tokens. This migration
 * adds platform_id binding to carts to enforce platform ownership validation.
 *
 * When platform_id is set (not null), the cart belongs to that platform exclusively.
 * Customer carts (created via website) have platform_id = NULL.
 *
 * Date: December 7, 2025
 */

-- Add platform_id column (nullable for customer carts)
ALTER TABLE carts
    ADD COLUMN platform_id UUID;

-- Add foreign key constraint to platforms table
ALTER TABLE carts
    ADD CONSTRAINT fk_carts_platform
        FOREIGN KEY (platform_id)
            REFERENCES platforms (id)
            ON DELETE SET NULL;

-- Add index for platform cart queries
CREATE INDEX idx_cart_platform_id ON carts (platform_id)
    WHERE platform_id IS NOT NULL;

-- Add comment explaining purpose
COMMENT ON COLUMN carts.platform_id IS
    'Platform that created this reservation. NULL for customer carts. Once set, cart is bound to that platform and cannot be accessed by other platforms (security: prevent cross-platform booking theft).';

-- Verification query (should return 0 initially since all existing carts are customer carts)
-- SELECT COUNT(*) FROM carts WHERE platform_id IS NOT NULL;
