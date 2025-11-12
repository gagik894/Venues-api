-- ================================================================
-- V18: Fix cart_tables foreign key to reference carts(id) not carts(token)
-- ================================================================
-- Problem: cart_tables.cart_id was UUID referencing carts(token)
-- Solution: Change to BIGINT referencing carts(id)
-- This aligns with JPA entity @ManyToOne relationship

-- Step 1: Drop the wrong foreign key constraint
ALTER TABLE cart_tables
    DROP CONSTRAINT IF EXISTS fk_cart_table_cart;

-- Step 2: Add temporary column with correct type
ALTER TABLE cart_tables
    ADD COLUMN cart_id_new BIGINT;

-- Step 3: Populate new column by joining with carts on token
UPDATE cart_tables ct
SET cart_id_new = c.id
FROM carts c
WHERE ct.cart_id::text = c.token::text;

-- Step 4: Drop old cart_id column
ALTER TABLE cart_tables
    DROP COLUMN cart_id;

-- Step 5: Rename new column to cart_id
ALTER TABLE cart_tables
    RENAME COLUMN cart_id_new TO cart_id;

-- Step 6: Set NOT NULL constraint
ALTER TABLE cart_tables
    ALTER COLUMN cart_id SET NOT NULL;

-- Step 7: Add correct foreign key constraint
ALTER TABLE cart_tables
    ADD CONSTRAINT fk_cart_table_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts (id)
            ON DELETE CASCADE;

-- Step 8: Recreate index with correct column
DROP INDEX IF EXISTS idx_cart_table_cart;
CREATE INDEX idx_cart_table_cart ON cart_tables (cart_id);

-- Verification: check column types match
SELECT 'carts.id' as column_name,
       data_type,
       udt_name
FROM information_schema.columns
WHERE table_name = 'carts'
  AND column_name = 'id'
UNION ALL
SELECT 'cart_tables.cart_id',
       data_type,
       udt_name
FROM information_schema.columns
WHERE table_name = 'cart_tables'
  AND column_name = 'cart_id';

-- Expected result: both should be 'bigint'

COMMENT ON COLUMN cart_tables.cart_id IS 'Foreign key to carts.id (NOT carts.token)';

