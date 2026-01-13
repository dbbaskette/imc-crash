-- =============================================================================
-- CRASH Database Migration
-- Adds missing columns to existing tables and creates messages table
-- Safe to run multiple times (uses IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)
-- =============================================================================

-- =============================================================================
-- Policies table - add coverage columns and deductible
-- =============================================================================
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_comprehensive BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_collision BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_liability BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_medical BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_uninsured BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_roadside BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS has_rental BOOLEAN DEFAULT true;
ALTER TABLE policies ADD COLUMN IF NOT EXISTS deductible INTEGER DEFAULT 500;

-- =============================================================================
-- Vehicles table - add license_plate and estimated_value
-- =============================================================================
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS license_plate VARCHAR(20);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS estimated_value INTEGER;

-- =============================================================================
-- Drivers table - add contact and emergency info columns
-- =============================================================================
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS customer_id INTEGER REFERENCES customers(customer_id);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS risk_score INTEGER DEFAULT 75;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS emergency_contact_name VARCHAR(100);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS emergency_contact_phone VARCHAR(20);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS is_primary BOOLEAN DEFAULT true;

-- =============================================================================
-- Messages table (for demo mode - stores intercepted emails and SMS)
-- =============================================================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('EMAIL', 'SMS', 'PUSH')),
    recipient_type VARCHAR(20) NOT NULL CHECK (recipient_type IN ('ADJUSTER', 'CUSTOMER')),
    recipient_identifier VARCHAR(255),
    claim_reference VARCHAR(100),
    subject VARCHAR(500),
    body TEXT,
    sent_at TIMESTAMP NOT NULL,
    customer_name VARCHAR(255),
    policy_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for messages if they don't exist
CREATE INDEX IF NOT EXISTS idx_messages_claim ON messages(claim_reference);
CREATE INDEX IF NOT EXISTS idx_messages_recipient ON messages(recipient_type, recipient_identifier);
CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_customer ON messages(customer_name);

-- =============================================================================
-- FNOL Reports table (stores processed FNOL reports from crash-orchestrator)
-- =============================================================================
CREATE TABLE IF NOT EXISTS fnol_reports (
    id BIGSERIAL PRIMARY KEY,
    claim_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    policy_id INTEGER NOT NULL,
    vehicle_id INTEGER,
    driver_id INTEGER,
    vin VARCHAR(20),
    event_time TIMESTAMP NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    street VARCHAR(255),
    severity VARCHAR(20) NOT NULL,
    impact_type VARCHAR(50),
    speed_at_impact DOUBLE PRECISION,
    g_force DOUBLE PRECISION,
    was_speeding BOOLEAN,
    airbag_deployed BOOLEAN,
    weather_conditions VARCHAR(100),
    road_surface VARCHAR(50),
    daylight_status VARCHAR(20),
    policy_number VARCHAR(50),
    driver_name VARCHAR(255),
    driver_phone VARCHAR(50),
    driver_email VARCHAR(255),
    vehicle_make VARCHAR(100),
    vehicle_model VARCHAR(100),
    vehicle_year INTEGER,
    sms_sent BOOLEAN,
    push_sent BOOLEAN,
    adjuster_notified BOOLEAN,
    assigned_adjuster VARCHAR(255),
    roadside_dispatched BOOLEAN,
    report_json TEXT NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for fnol_reports
CREATE INDEX IF NOT EXISTS idx_fnol_claim_number ON fnol_reports(claim_number);
CREATE INDEX IF NOT EXISTS idx_fnol_policy_id ON fnol_reports(policy_id);
CREATE INDEX IF NOT EXISTS idx_fnol_severity ON fnol_reports(severity);
CREATE INDEX IF NOT EXISTS idx_fnol_event_time ON fnol_reports(event_time DESC);
CREATE INDEX IF NOT EXISTS idx_fnol_status ON fnol_reports(status);

-- =============================================================================
-- Create indexes on existing tables if they don't exist
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_policies_policy_id ON policies(policy_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_policy_id ON vehicles(policy_id);
CREATE INDEX IF NOT EXISTS idx_drivers_policy_id ON drivers(policy_id);
CREATE INDEX IF NOT EXISTS idx_drivers_driver_id ON drivers(driver_id);

-- =============================================================================
-- Summary
-- =============================================================================
SELECT 'Migration completed successfully!' AS status;
SELECT 'Policies columns: ' || COUNT(*) FROM information_schema.columns WHERE table_name = 'policies';
SELECT 'Vehicles columns: ' || COUNT(*) FROM information_schema.columns WHERE table_name = 'vehicles';
SELECT 'Drivers columns: ' || COUNT(*) FROM information_schema.columns WHERE table_name = 'drivers';
SELECT 'Messages table exists: ' || EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = 'messages');
SELECT 'FNOL Reports table exists: ' || EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = 'fnol_reports');
