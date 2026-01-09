-- =============================================================================
-- CRASH Database Schema
-- Simplified schema for policy lookups (from imc-schema)
-- Matches drivers from telematics generator (drivers.json)
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
-- Sample Data - Matches telematics generator drivers.json
-- All 15 drivers in Atlanta, GA area
-- =============================================================================

-- Customers (100001-100015)
INSERT INTO customers (customer_id, first_name, last_name, email, phone_number, address, city, state, zip_code) VALUES
(100001, 'Sarah', 'Chen', 'sarah.chen@email.com', '+1-404-555-0101', '123 Peachtree St NE', 'Atlanta', 'GA', '30308'),
(100002, 'Emily', 'Carter', 'emily.carter@email.com', '+1-404-555-0102', '456 Ponce de Leon Ave', 'Atlanta', 'GA', '30308'),
(100003, 'Benjamin', 'Rivera', 'benjamin.rivera@email.com', '+1-404-555-0103', '789 Piedmont Ave', 'Atlanta', 'GA', '30309'),
(100004, 'Michael', 'Harris', 'michael.harris@email.com', '+1-404-555-0104', '321 North Ave NW', 'Atlanta', 'GA', '30332'),
(100005, 'David', 'Lee', 'david.lee@email.com', '+1-404-555-0105', '654 Marietta St NW', 'Atlanta', 'GA', '30313'),
(100006, 'Jessica', 'Thompson', 'jessica.thompson@email.com', '+1-404-555-0106', '987 Spring St NW', 'Atlanta', 'GA', '30309'),
(100007, 'Andrew', 'Martinez', 'andrew.martinez@email.com', '+1-404-555-0107', '147 Techwood Dr', 'Atlanta', 'GA', '30313'),
(100008, 'Ashley', 'Wilson', 'ashley.wilson@email.com', '+1-404-555-0108', '258 Centennial Olympic Park Dr', 'Atlanta', 'GA', '30313'),
(100009, 'Christopher', 'Garcia', 'christopher.garcia@email.com', '+1-404-555-0109', '369 Baker St NW', 'Atlanta', 'GA', '30313'),
(100010, 'Amanda', 'Rodriguez', 'amanda.rodriguez@email.com', '+1-404-555-0110', '741 Williams St NW', 'Atlanta', 'GA', '30332'),
(100011, 'Daniel', 'Johnson', 'daniel.johnson@email.com', '+1-404-555-0111', '852 Luckie St NW', 'Atlanta', 'GA', '30313'),
(100012, 'Lauren', 'Brown', 'lauren.brown@email.com', '+1-404-555-0112', '963 Walton St NW', 'Atlanta', 'GA', '30313'),
(100013, 'Matthew', 'Davis', 'matthew.davis@email.com', '+1-404-555-0113', '159 Ted Turner Dr NW', 'Atlanta', 'GA', '30303'),
(100014, 'Stephanie', 'Miller', 'stephanie.miller@email.com', '+1-404-555-0114', '267 Alabama St SW', 'Atlanta', 'GA', '30303'),
(100015, 'Ryan', 'Anderson', 'ryan.anderson@email.com', '+1-404-555-0115', '378 Pryor St SW', 'Atlanta', 'GA', '30303');

-- Policies (200001-200015, matching telematics generator policy_id)
INSERT INTO policies (policy_id, customer_id, policy_number, start_date, end_date, status, deductible, has_comprehensive, has_collision, has_liability, has_medical, has_uninsured, has_roadside, has_rental) VALUES
(200001, 100001, 'IMC-200001', '2024-01-15', '2025-01-14', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200002, 100002, 'IMC-200002', '2024-02-20', '2025-02-19', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200003, 100003, 'IMC-200003', '2024-03-10', '2025-03-09', 'ACTIVE', 1000, true, true, true, true, true, true, true),
(200004, 100004, 'IMC-200004', '2024-03-01', '2025-02-28', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200005, 100005, 'IMC-200005', '2024-04-05', '2025-04-04', 'ACTIVE', 250, true, true, true, true, true, true, true),
(200006, 100006, 'IMC-200006', '2024-05-01', '2025-04-30', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200007, 100007, 'IMC-200007', '2024-06-15', '2025-06-14', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200008, 100008, 'IMC-200008', '2024-07-01', '2025-06-30', 'ACTIVE', 750, true, true, true, true, true, true, true),
(200009, 100009, 'IMC-200009', '2024-08-10', '2025-08-09', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200010, 100010, 'IMC-200010', '2024-09-01', '2025-08-31', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200011, 100011, 'IMC-200011', '2024-10-15', '2025-10-14', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200012, 100012, 'IMC-200012', '2024-11-01', '2025-10-31', 'ACTIVE', 1000, true, true, true, true, true, true, true),
(200013, 100013, 'IMC-200013', '2024-12-01', '2025-11-30', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200014, 100014, 'IMC-200014', '2025-01-01', '2025-12-31', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200015, 100015, 'IMC-200015', '2025-01-15', '2026-01-14', 'ACTIVE', 500, true, true, true, true, true, true, true);

-- Vehicles (matching telematics generator vehicle_id and VINs)
INSERT INTO vehicles (vehicle_id, policy_id, vin, make, model, year, color, license_plate, estimated_value) VALUES
(300001, 200001, '1G1RC6E45F0123451', 'Chevrolet', 'Equinox', 2023, 'Silver', 'GA-1001', 32000),
(300002, 200002, 'JN8AZ2CP9G0123452', 'Nissan', 'Rogue', 2022, 'Blue', 'GA-1002', 28000),
(300003, 200003, '5YJSA1E2XLH987653', 'Tesla', 'Model 3', 2023, 'Red', 'GA-1003', 45000),
(300004, 200004, 'WA1VBFGE3L0123454', 'Audi', 'Q5', 2021, 'Black', 'GA-1004', 42000),
(300006, 200005, '1FMCU0KH1M0123456', 'Ford', 'Explorer', 2022, 'White', 'GA-1005', 38000),
(300007, 200006, '3FA6P0SU2N0123457', 'Ford', 'F-150', 2023, 'Gray', 'GA-1006', 52000),
(300008, 200007, '4T1B61HK5P0123458', 'Toyota', 'Camry', 2023, 'Silver', 'GA-1007', 28000),
(300009, 200008, '1HGCP2F53N0123459', 'Honda', 'CR-V', 2023, 'Blue', 'GA-1008', 32000),
(300010, 200009, 'KNDP34AC6P0123460', 'Kia', 'Telluride', 2023, 'White', 'GA-1009', 46000),
(300011, 200010, '5NPE24AB5P0123461', 'Hyundai', 'Palisade', 2023, 'Black', 'GA-1010', 48000),
(300013, 200011, '1C4RJFAG9P0123463', 'Jeep', 'Grand Cherokee', 2023, 'Green', 'GA-1011', 55000),
(300014, 200012, 'WBA33AG01P0123464', 'BMW', 'X5', 2022, 'White', 'GA-1012', 62000),
(300015, 200013, 'JM1KF2F35P0123465', 'Mazda', 'CX-5', 2023, 'Red', 'GA-1013', 32000),
(300016, 200014, 'JTDKARFH6P0123466', 'Lexus', 'RX 350', 2023, 'Pearl White', 'GA-1014', 58000),
(300017, 200015, '4S3BN7AN2P0123467', 'Subaru', 'Outback', 2023, 'Blue', 'GA-1015', 35000);

-- Drivers (matching telematics generator driver_id)
-- Note: driver_ids 400001-400004, 400006-400011, 400013-400017 (400005 and 400012 are skipped in telematics)
INSERT INTO drivers (driver_id, policy_id, customer_id, first_name, last_name, phone_number, email, date_of_birth, license_number, risk_score, emergency_contact_name, emergency_contact_phone, is_primary) VALUES
(400001, 200001, 100001, 'Sarah', 'Chen', '+1-404-555-0101', 'sarah.chen@email.com', '1990-05-15', 'GA-C12345001', 82, 'John Chen', '+1-404-555-9901', true),
(400002, 200002, 100002, 'Emily', 'Carter', '+1-404-555-0102', 'emily.carter@email.com', '1988-11-22', 'GA-C23456002', 78, 'James Carter', '+1-404-555-9902', true),
(400003, 200003, 100003, 'Benjamin', 'Rivera', '+1-404-555-0103', 'benjamin.rivera@email.com', '1995-02-10', 'GA-R34567003', 71, 'Maria Rivera', '+1-404-555-9903', true),
(400004, 200004, 100004, 'Michael', 'Harris', '+1-404-555-0104', 'michael.harris@email.com', '1992-08-30', 'GA-H45678004', 85, 'Susan Harris', '+1-404-555-9904', true),
(400006, 200005, 100005, 'David', 'Lee', '+1-404-555-0105', 'david.lee@email.com', '1985-12-01', 'GA-L56789006', 88, 'Linda Lee', '+1-404-555-9905', true),
(400007, 200006, 100006, 'Jessica', 'Thompson', '+1-404-555-0106', 'jessica.thompson@email.com', '1991-07-18', 'GA-T67890007', 65, 'Robert Thompson', '+1-404-555-9906', true),
(400008, 200007, 100007, 'Andrew', 'Martinez', '+1-404-555-0107', 'andrew.martinez@email.com', '1987-03-25', 'GA-M78901008', 79, 'Carmen Martinez', '+1-404-555-9907', true),
(400009, 200008, 100008, 'Ashley', 'Wilson', '+1-404-555-0108', 'ashley.wilson@email.com', '1993-09-12', 'GA-W89012009', 81, 'Brian Wilson', '+1-404-555-9908', true),
(400010, 200009, 100009, 'Christopher', 'Garcia', '+1-404-555-0109', 'christopher.garcia@email.com', '1989-06-05', 'GA-G90123010', 76, 'Maria Garcia', '+1-404-555-9909', true),
(400011, 200010, 100010, 'Amanda', 'Rodriguez', '+1-404-555-0110', 'amanda.rodriguez@email.com', '1994-01-28', 'GA-R01234011', 83, 'Carlos Rodriguez', '+1-404-555-9910', true),
(400013, 200011, 100011, 'Daniel', 'Johnson', '+1-404-555-0111', 'daniel.johnson@email.com', '1986-04-14', 'GA-J12345013', 77, 'Michelle Johnson', '+1-404-555-9911', true),
(400014, 200012, 100012, 'Lauren', 'Brown', '+1-404-555-0112', 'lauren.brown@email.com', '1996-10-03', 'GA-B23456014', 62, 'Kevin Brown', '+1-404-555-9912', true),
(400015, 200013, 100013, 'Matthew', 'Davis', '+1-404-555-0113', 'matthew.davis@email.com', '1990-12-20', 'GA-D34567015', 84, 'Jennifer Davis', '+1-404-555-9913', true),
(400016, 200014, 100014, 'Stephanie', 'Miller', '+1-404-555-0114', 'stephanie.miller@email.com', '1992-08-08', 'GA-M45678016', 80, 'David Miller', '+1-404-555-9914', true),
(400017, 200015, 100015, 'Ryan', 'Anderson', '+1-404-555-0115', 'ryan.anderson@email.com', '1988-05-17', 'GA-A56789017', 75, 'Lisa Anderson', '+1-404-555-9915', true);

-- Confirm data loaded
SELECT 'Schema created successfully!' AS status;
SELECT 'Customers: ' || COUNT(*) FROM customers;
SELECT 'Policies: ' || COUNT(*) FROM policies;
SELECT 'Vehicles: ' || COUNT(*) FROM vehicles;
SELECT 'Drivers: ' || COUNT(*) FROM drivers;
