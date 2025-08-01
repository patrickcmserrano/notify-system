-- Down migration for simple initial tables
DROP TABLE IF EXISTS notification_preferences CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;  
DROP TABLE IF EXISTS notification_templates CASCADE;
DROP TABLE IF EXISTS users CASCADE;
