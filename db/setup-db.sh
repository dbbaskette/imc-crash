#!/bin/bash

# CRASH Database Setup Script
# Sets up the policy database schema and sample data

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default connection details (matching docker-compose.yml)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-crash}"
DB_USER="${DB_USER:-crash}"
DB_PASSWORD="${DB_PASSWORD:-crash}"

echo "================================================"
echo "CRASH Database Setup"
echo "================================================"
echo "Host: $DB_HOST:$DB_PORT"
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo ""

# Check if PostgreSQL is running
echo "Checking database connection..."
export PGPASSWORD=$DB_PASSWORD

if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
    echo "ERROR: Cannot connect to database."
    echo "Make sure PostgreSQL is running (docker-compose up -d postgres)"
    exit 1
fi

echo "✓ Database connection successful"
echo ""

# Run the schema setup
echo "Creating schema and loading sample data..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SCRIPT_DIR/init-schema.sql"

unset PGPASSWORD

echo ""
echo "================================================"
echo "✓ Database setup complete!"
echo "================================================"
echo ""
echo "15 drivers loaded (matching telematics generator):"
echo "  Drivers: 400001-400004, 400006-400011, 400013-400017"
echo "  Policies: 200001-200015"
echo "  Vehicles: 300001-300004, 300006-300011, 300013-300017"
echo ""
