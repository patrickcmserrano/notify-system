-- Down migration for categories
ALTER TABLE notifications DROP COLUMN IF EXISTS category_id;
DROP TABLE IF EXISTS user_channel_preferences CASCADE;
DROP TABLE IF EXISTS user_category_subscriptions CASCADE;
DROP TABLE IF EXISTS notification_channels CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
