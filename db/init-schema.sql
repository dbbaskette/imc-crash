-- =============================================================================
-- CRASH Database Schema
-- Simplified schema for policy lookups (from imc-schema)
-- =============================================================================

-- Drop existing tables if they exist
DROP TABLE IF EXISTS drivers CASCADE;
DROP TABLE IF EXISTS vehicles CASCADE;
DROP TABLE IF EXISTS policies CASCADE;
DROP TABLE IF EXISTS customers CASCADE;

-- =============================================================================
-- Customers table
-- =============================================================================
CREATE TABLE customers (
    customer_id SERIAL PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(2),
    zip_code VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Policies table
-- =============================================================================
CREATE TABLE policies (
    policy_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
    policy_number VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    -- Coverage details
    has_comprehensive BOOLEAN DEFAULT true,
    has_collision BOOLEAN DEFAULT true,
    has_liability BOOLEAN DEFAULT true,
    has_medical BOOLEAN DEFAULT true,
    has_uninsured BOOLEAN DEFAULT true,
    has_roadside BOOLEAN DEFAULT true,
    has_rental BOOLEAN DEFAULT true,
    deductible INTEGER DEFAULT 500,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Vehicles table
-- =============================================================================
CREATE TABLE vehicles (
    vehicle_id SERIAL PRIMARY KEY,
    policy_id INTEGER NOT NULL REFERENCES policies(policy_id),
    vin VARCHAR(17) NOT NULL,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INTEGER NOT NULL,
    color VARCHAR(30),
    license_plate VARCHAR(20),
    estimated_value INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Drivers table
-- =============================================================================
CREATE TABLE drivers (
    driver_id SERIAL PRIMARY KEY,
    policy_id INTEGER NOT NULL REFERENCES policies(policy_id),
    customer_id INTEGER REFERENCES customers(customer_id),
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20),
    email VARCHAR(255),
    date_of_birth DATE,
    license_number VARCHAR(20),
    risk_score INTEGER DEFAULT 75,
    emergency_contact_name VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    is_primary BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common lookups
CREATE INDEX idx_policies_policy_id ON policies(policy_id);
CREATE INDEX idx_vehicles_policy_id ON vehicles(policy_id);
CREATE INDEX idx_drivers_policy_id ON drivers(policy_id);
CREATE INDEX idx_drivers_driver_id ON drivers(driver_id);

-- =============================================================================
-- Sample Data
-- =============================================================================

-- Customers
INSERT INTO customers (customer_id, first_name, last_name, email, phone_number, address, city, state, zip_code) VALUES
(100001, 'Sarah', 'Chen', 'sarah.chen@email.com', '555-0101', '789 Maple Drive', 'Springfield', 'IL', '62704'),
(100002, 'Emily', 'Carter', 'emily.carter@email.com', '555-0102', '456 Oak Ave', 'Austin', 'TX', '78701'),
(100003, 'Benjamin', 'Rivera', 'benjamin.rivera@email.com', '555-0103', '789 Pine Ln', 'Miami', 'FL', '33101'),
(100004, 'Michael', 'Harris', 'michael.harris@email.com', '555-0104', '452 Oakwood Ave', 'Columbus', 'OH', '43215'),
(100005, 'David', 'Lee', 'david.lee@email.com', '555-0105', '123 Main St', 'San Jose', 'CA', '95101'),
(100018, 'Evelyn', 'Moore', 'evelyn.moore@email.com', '555-0118', '321 Cedar Blvd', 'Reston', 'VA', '20190'),
(100019, 'Alexander', 'Jackson', 'alexander.jackson@email.com', '555-0119', '654 Birch St', 'Arlington', 'VA', '22201'),
(100020, 'Harper', 'Martin', 'harper.martin@email.com', '555-0120', '987 Willow Way', 'Fairfax', 'VA', '22030');

-- Policies (IDs match what telematics generator sends: 200018, 200019, 200020)
INSERT INTO policies (policy_id, customer_id, policy_number, start_date, end_date, status, deductible) VALUES
(200001, 100001, 'IMC-200001', '2024-01-15', '2025-01-14', 'ACTIVE', 500),
(200002, 100002, 'IMC-200002', '2024-02-20', '2025-02-19', 'ACTIVE', 500),
(200003, 100003, 'IMC-200003', '2024-03-10', '2025-03-09', 'ACTIVE', 1000),
(200004, 100004, 'IMC-200004', '2024-03-01', '2025-02-28', 'ACTIVE', 500),
(200005, 100005, 'IMC-200005', '2024-04-05', '2025-04-04', 'ACTIVE', 250),
(200018, 100018, 'IMC-200018', '2024-01-01', '2025-01-01', 'ACTIVE', 500),
(200019, 100019, 'IMC-200019', '2024-02-01', '2025-02-01', 'ACTIVE', 500),
(200020, 100020, 'IMC-200020', '2024-03-01', '2025-03-01', 'ACTIVE', 500);

-- Vehicles
INSERT INTO vehicles (vehicle_id, policy_id, vin, make, model, year, color, license_plate, estimated_value) VALUES
(300001, 200001, '1G1RC6E45F0123451', 'Chevrolet', 'Equinox', 2023, 'Silver', 'IL-ABC-1234', 32000),
(300002, 200002, 'JN8AZ2CP9G0123452', 'Nissan', 'Rogue', 2022, 'Blue', 'TX-DEF-5678', 28000),
(300003, 200003, '5YJSA1E2XLH987653', 'Tesla', 'Model 3', 2023, 'Red', 'FL-GHI-9012', 45000),
(300004, 200004, 'WA1VBFGE3L0123454', 'Audi', 'Q5', 2021, 'Black', 'OH-JKL-3456', 42000),
(300005, 200005, '2T3H11BC4N0123455', 'Toyota', 'RAV4', 2023, 'White', 'CA-MNO-7890', 35000),
(300018, 200018, '1HGCV1F3XP0123470', 'Honda', 'Civic', 2023, 'Aegean Blue', 'VA-XYZ-1821', 28000),
(300019, 200018, '1HGFC1F7XP0123471', 'Honda', 'Accord', 2022, 'Modern Steel', 'VA-ABC-1822', 32000),
(300020, 200019, '5NPD84AE7P0123472', 'Hyundai', 'Sonata', 2023, 'Portofino Gray', 'VA-DEF-1923', 30000),
(300021, 200020, 'KNAJU4B2XP0123473', 'Kia', 'K5', 2023, 'Glacial White Pearl', 'VA-GHI-2024', 29000);

-- Drivers (IDs match what telematics generator sends: 400021, 400023, 400024)
INSERT INTO drivers (driver_id, policy_id, customer_id, first_name, last_name, phone_number, email, date_of_birth, license_number, risk_score, emergency_contact_name, emergency_contact_phone, is_primary) VALUES
(400001, 200001, 100001, 'Sarah', 'Chen', '+1-217-555-0101', 'sarah.chen@email.com', '1990-05-15', 'IL-C12345678', 82, 'John Chen', '+1-217-555-0199', true),
(400002, 200002, 100002, 'Emily', 'Carter', '+1-512-555-0102', 'emily.carter@email.com', '1988-11-22', 'TX-C23456789', 78, 'James Carter', '+1-512-555-0199', true),
(400003, 200003, 100003, 'Benjamin', 'Rivera', '+1-305-555-0103', 'benjamin.rivera@email.com', '1995-02-10', 'FL-R34567890', 71, 'Maria Rivera', '+1-305-555-0199', true),
(400004, 200004, 100004, 'Michael', 'Harris', '+1-614-555-0104', 'michael.harris@email.com', '1992-08-30', 'OH-H45678901', 85, 'Susan Harris', '+1-614-555-0199', true),
(400005, 200005, 100005, 'David', 'Lee', '+1-408-555-0105', 'david.lee@email.com', '1985-12-01', 'CA-L56789012', 88, 'Linda Lee', '+1-408-555-0199', true),
(400021, 200018, 100018, 'Evelyn', 'Moore', '+1-703-555-0118', 'evelyn.moore@email.com', '1997-11-02', 'VA-M89012346', 76, 'Robert Moore', '+1-703-555-0199', true),
(400022, 200018, NULL, 'Jack', 'Moore', '+1-703-555-0122', 'jack.moore@email.com', '1996-09-01', 'VA-M89012347', 72, 'Evelyn Moore', '+1-703-555-0118', false),
(400023, 200019, 100019, 'Alexander', 'Jackson', '+1-571-555-0119', 'alexander.jackson@email.com', '1984-04-25', 'VA-J90123457', 81, 'Lisa Jackson', '+1-571-555-0199', true),
(400024, 200020, 100020, 'Harper', 'Martin', '+1-703-555-0120', 'harper.martin@email.com', '2002-07-30', 'VA-M01234568', 68, 'Tom Martin', '+1-703-555-0199', true);

-- Confirm data loaded
SELECT 'Schema created successfully!' AS status;
SELECT 'Customers: ' || COUNT(*) FROM customers;
SELECT 'Policies: ' || COUNT(*) FROM policies;
SELECT 'Vehicles: ' || COUNT(*) FROM vehicles;
SELECT 'Drivers: ' || COUNT(*) FROM drivers;
