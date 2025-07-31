-- Migration: add-categories-and-preferences
-- Down migration

-- Remove triggers
DROP TRIGGER IF EXISTS update_user_channel_preferences_updated_at ON user_channel_preferences;
DROP TRIGGER IF EXISTS update_notification_channels_updated_at ON notification_channels;
DROP TRIGGER IF EXISTS update_categories_updated_at ON categories;

-- Remove indexes
DROP INDEX IF EXISTS idx_notifications_category_id;
DROP INDEX IF EXISTS idx_user_channel_preferences_channel_id;
DROP INDEX IF EXISTS idx_user_channel_preferences_user_id;
DROP INDEX IF EXISTS idx_user_category_subscriptions_category_id;
DROP INDEX IF EXISTS idx_user_category_subscriptions_user_id;
DROP INDEX IF EXISTS idx_notification_channels_active;
DROP INDEX IF EXISTS idx_notification_channels_name;
DROP INDEX IF EXISTS idx_categories_active;
DROP INDEX IF EXISTS idx_categories_name;

-- Remove column from notifications table
ALTER TABLE notifications DROP COLUMN IF EXISTS category_id;

-- Remove column from users table
ALTER TABLE users DROP COLUMN IF EXISTS phone;

-- Remove tables (in reverse order due to foreign keys)
DROP TABLE IF EXISTS user_channel_preferences;
DROP TABLE IF EXISTS user_category_subscriptions;
DROP TABLE IF EXISTS notification_channels;
DROP TABLE IF EXISTS categories;
