#!/bin/bash

# Database Initialization Script
# This script sets up the database with all required data

echo "=== Notification System Database Setup ==="
echo "Starting database initialization..."

# Check if database is running
if ! docker exec notify-postgres pg_isready -U notify_user -d notifications > /dev/null 2>&1; then
    echo "ERROR: Database is not running. Please start it with: docker compose up -d"
    exit 1
fi

echo "Database is running"

# Reset database (optional - comment out if you want to preserve existing data)
echo "Resetting database schema..."
docker exec -it notify-postgres psql -U notify_user -d notifications -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" > /dev/null 2>&1

# Recreate UUID extension
echo "Creating UUID extension..."
docker exec -it notify-postgres psql -U notify_user -d notifications -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";" > /dev/null 2>&1

# Run migrations
echo "Running database migrations..."
echo "  - Creating initial tables..."
docker exec -i notify-postgres psql -U notify_user -d notifications < resources/migrations/001-create-initial-tables-simple.up.sql > /dev/null 2>&1

echo "  - Adding categories and preferences..."
docker exec -i notify-postgres psql -U notify_user -d notifications < resources/migrations/002-add-categories-simple.up.sql > /dev/null 2>&1

# Seed data
echo "Seeding initial data..."
docker exec -i notify-postgres psql -U notify_user -d notifications < seed-data.sql > /dev/null 2>&1

echo "Database setup completed successfully!"
echo ""
echo "Seeded data:"
echo "- Categories: Sports, Finance, Movies"
echo "- Channels: SMS, Email, Push"
echo "- Sample users: John Doe, Jane Smith, Bob Wilson"
echo ""
echo "=== Setup Complete ==="
