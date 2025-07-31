-- Manual seeding for categories
INSERT INTO categories (name, description) VALUES 
  ('Sports', 'Notifications related to Sports'),
  ('Finance', 'Notifications related to Finance'),
  ('Movies', 'Notifications related to Movies')
ON CONFLICT (name) DO NOTHING;

-- Manual seeding for notification channels
INSERT INTO notification_channels (name, description) VALUES 
  ('SMS', 'Short Message Service notifications'),
  ('Email', 'Email notifications'),
  ('Push', 'Push notifications')
ON CONFLICT (name) DO NOTHING;

-- Manual seeding for sample users
INSERT INTO users (email, name, phone) VALUES 
  ('john.doe@example.com', 'John Doe', '+1234567890'),
  ('jane.smith@example.com', 'Jane Smith', '+1234567891'),
  ('bob.wilson@example.com', 'Bob Wilson', '+1234567892')
ON CONFLICT (email) DO UPDATE SET 
  name = EXCLUDED.name, 
  phone = EXCLUDED.phone;

-- Get user IDs and set up subscriptions
DO $$
DECLARE 
    john_id UUID;
    jane_id UUID;
    bob_id UUID;
    sports_id UUID;
    finance_id UUID;
    movies_id UUID;
    sms_id UUID;
    email_id UUID;
    push_id UUID;
BEGIN
    -- Get user IDs
    SELECT id INTO john_id FROM users WHERE email = 'john.doe@example.com';
    SELECT id INTO jane_id FROM users WHERE email = 'jane.smith@example.com';
    SELECT id INTO bob_id FROM users WHERE email = 'bob.wilson@example.com';
    
    -- Get category IDs
    SELECT id INTO sports_id FROM categories WHERE name = 'Sports';
    SELECT id INTO finance_id FROM categories WHERE name = 'Finance';
    SELECT id INTO movies_id FROM categories WHERE name = 'Movies';
    
    -- Get channel IDs
    SELECT id INTO sms_id FROM notification_channels WHERE name = 'SMS';
    SELECT id INTO email_id FROM notification_channels WHERE name = 'Email';
    SELECT id INTO push_id FROM notification_channels WHERE name = 'Push';
    
    -- John's subscriptions: Sports, Finance
    INSERT INTO user_category_subscriptions (user_id, category_id) VALUES 
      (john_id, sports_id), (john_id, finance_id)
    ON CONFLICT (user_id, category_id) DO NOTHING;
    
    -- John's channels: SMS, Email
    INSERT INTO user_channel_preferences (user_id, channel_id, enabled) VALUES 
      (john_id, sms_id, true), (john_id, email_id, true)
    ON CONFLICT (user_id, channel_id) DO UPDATE SET enabled = EXCLUDED.enabled;
    
    -- Jane's subscriptions: Movies, Finance
    INSERT INTO user_category_subscriptions (user_id, category_id) VALUES 
      (jane_id, movies_id), (jane_id, finance_id)
    ON CONFLICT (user_id, category_id) DO NOTHING;
    
    -- Jane's channels: Email, Push
    INSERT INTO user_channel_preferences (user_id, channel_id, enabled) VALUES 
      (jane_id, email_id, true), (jane_id, push_id, true)
    ON CONFLICT (user_id, channel_id) DO UPDATE SET enabled = EXCLUDED.enabled;
    
    -- Bob's subscriptions: Sports, Movies
    INSERT INTO user_category_subscriptions (user_id, category_id) VALUES 
      (bob_id, sports_id), (bob_id, movies_id)
    ON CONFLICT (user_id, category_id) DO NOTHING;
    
    -- Bob's channels: SMS, Push
    INSERT INTO user_channel_preferences (user_id, channel_id, enabled) VALUES 
      (bob_id, sms_id, true), (bob_id, push_id, true)
    ON CONFLICT (user_id, channel_id) DO UPDATE SET enabled = EXCLUDED.enabled;
END $$;
