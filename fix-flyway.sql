-- ================================================================
-- FIX FLYWAY SCHEMA HISTORY AFTER MANUAL TABLE DELETION
-- ================================================================
--
-- Problem: Venue tables were manually deleted, but Flyway still
--          thinks V3 migration was applied.
--
-- Solution: Remove V3 record from flyway_schema_history so Flyway
--           will re-run it.
--
-- IMPORTANT: Run this script in your PostgreSQL database BEFORE
--            starting the application.
-- ================================================================

-- Check current Flyway migration status
SELECT version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Remove V3 migration record (venue tables)
-- This tells Flyway that V3 hasn't been applied yet
DELETE
FROM flyway_schema_history
WHERE version = '3'
  AND script = 'V3__create_venues_tables.sql';

-- Verify V3 is removed
SELECT version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Now restart your application and Flyway will re-run V3 to create the venue tables

