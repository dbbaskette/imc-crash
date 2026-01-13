-- =============================================================================
-- CRASH Data Population Script
-- Adds missing customers/policies and updates existing records with new fields
-- Safe to run multiple times (uses ON CONFLICT DO NOTHING / UPDATE)
-- =============================================================================

-- =============================================================================
-- Add missing customers (100016-100025)
-- =============================================================================
INSERT INTO customers (customer_id, first_name, last_name, email, phone_number, address, city, state, zip_code) VALUES
(100016, 'Michelle', 'Wong', 'michelle.wong@email.com', '+1-770-555-0116', '4300 Paces Ferry Rd', 'Vinings', 'GA', '30339'),
(100017, 'Jason', 'Patel', 'jason.patel@email.com', '+1-770-555-0117', '4279 Lawrenceville Hwy', 'Tucker', 'GA', '30084'),
(100018, 'Nicole', 'Foster', 'nicole.foster@email.com', '+1-404-555-0118', '3535 Main St', 'East Point', 'GA', '30344'),
(100019, 'Brandon', 'Kim', 'brandon.kim@email.com', '+1-770-555-0119', '6325 Highway 92', 'Woodstock', 'GA', '30189'),
(100020, 'Rachel', 'Cooper', 'rachel.cooper@email.com', '+1-770-555-0120', '5150 Buford Hwy', 'Chamblee', 'GA', '30341'),
(100021, 'Tyler', 'Morgan', 'tyler.morgan@email.com', '+1-770-555-0121', '6755 Douglas Blvd', 'Douglasville', 'GA', '30135'),
(100022, 'Samantha', 'Hayes', 'samantha.hayes@email.com', '+1-404-555-0122', '5920 Roswell Rd NE', 'Sandy Springs', 'GA', '30328'),
(100023, 'Kevin', 'O''Brien', 'kevin.obrien@email.com', '+1-770-555-0123', '1905 Scenic Hwy', 'Snellville', 'GA', '30078'),
(100024, 'Amy', 'Chen', 'amy.chen@email.com', '+1-770-555-0124', '3579 Highway 138', 'Stockbridge', 'GA', '30281'),
(100025, 'Marcus', 'Williams', 'marcus.williams@email.com', '+1-770-555-0125', '5455 Veterans Memorial Hwy', 'Mableton', 'GA', '30126')
ON CONFLICT (customer_id) DO NOTHING;

-- =============================================================================
-- Add missing policies (100016-100025) with coverage details
-- =============================================================================
INSERT INTO policies (policy_id, customer_id, policy_number, start_date, end_date, status, deductible, has_comprehensive, has_collision, has_liability, has_medical, has_uninsured, has_roadside, has_rental) VALUES
(200016, 100016, 'IMC-200016', '2024-02-01', '2025-01-31', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200017, 100017, 'IMC-200017', '2024-03-15', '2025-03-14', 'ACTIVE', 250, true, true, true, true, true, true, true),
(200018, 100018, 'IMC-200018', '2024-04-01', '2025-03-31', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200019, 100019, 'IMC-200019', '2024-05-15', '2025-05-14', 'ACTIVE', 750, true, true, true, true, true, true, true),
(200020, 100020, 'IMC-200020', '2024-06-01', '2025-05-31', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200021, 100021, 'IMC-200021', '2024-07-15', '2025-07-14', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200022, 100022, 'IMC-200022', '2024-08-01', '2025-07-31', 'ACTIVE', 1000, true, true, true, true, true, true, true),
(200023, 100023, 'IMC-200023', '2024-09-15', '2025-09-14', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200024, 100024, 'IMC-200024', '2024-10-01', '2025-09-30', 'ACTIVE', 500, true, true, true, true, true, true, true),
(200025, 100025, 'IMC-200025', '2024-11-15', '2025-11-14', 'ACTIVE', 500, true, true, true, true, true, true, true)
ON CONFLICT (policy_id) DO NOTHING;

-- =============================================================================
-- Update existing policies with coverage details (set defaults for old records)
-- =============================================================================
UPDATE policies SET
    has_comprehensive = COALESCE(has_comprehensive, true),
    has_collision = COALESCE(has_collision, true),
    has_liability = COALESCE(has_liability, true),
    has_medical = COALESCE(has_medical, true),
    has_uninsured = COALESCE(has_uninsured, true),
    has_roadside = COALESCE(has_roadside, true),
    has_rental = COALESCE(has_rental, true),
    deductible = COALESCE(deductible, 500)
WHERE has_comprehensive IS NULL OR deductible IS NULL;

-- =============================================================================
-- Update drivers with contact info (matching telematics generator)
-- =============================================================================
UPDATE drivers SET
    phone_number = c.phone_number,
    email = c.email,
    customer_id = c.customer_id,
    risk_score = COALESCE(risk_score, 75),
    is_primary = COALESCE(is_primary, true)
FROM customers c
WHERE drivers.first_name = c.first_name
  AND drivers.last_name = c.last_name
  AND drivers.phone_number IS NULL;

-- Set emergency contacts for primary drivers (generic pattern)
UPDATE drivers SET
    emergency_contact_name = 'Emergency Contact',
    emergency_contact_phone = '+1-770-555-9999'
WHERE emergency_contact_name IS NULL AND is_primary = true;

-- =============================================================================
-- Update vehicles with license plates and values (matching telematics generator)
-- =============================================================================
UPDATE vehicles SET license_plate = 'GA-' || LPAD((vehicle_id - 299000)::text, 4, '0')
WHERE license_plate IS NULL;

UPDATE vehicles SET estimated_value =
    CASE
        WHEN make = 'Tesla' THEN 45000
        WHEN make = 'BMW' THEN 55000
        WHEN make = 'Audi' THEN 50000
        WHEN make = 'Lexus' THEN 55000
        WHEN make = 'Land Rover' THEN 65000
        WHEN make IN ('GMC', 'Chevrolet') AND model LIKE '%Yukon%' OR model LIKE '%Tahoe%' THEN 60000
        WHEN make = 'Ford' AND model = 'F-150' THEN 52000
        WHEN make = 'Jeep' AND model = 'Grand Cherokee' THEN 55000
        WHEN make = 'Jeep' AND model = 'Wrangler' THEN 45000
        WHEN make IN ('Kia', 'Hyundai') AND model LIKE '%Telluride%' OR model LIKE '%Palisade%' THEN 46000
        WHEN make = 'Subaru' THEN 35000
        WHEN make = 'Honda' AND model = 'Pilot' THEN 42000
        WHEN make = 'Honda' AND model = 'CR-V' THEN 32000
        WHEN make = 'Toyota' AND model = 'Camry' THEN 28000
        WHEN make = 'Toyota' AND model = 'Corolla' THEN 24000
        WHEN make = 'Nissan' AND model = 'Altima' THEN 26000
        WHEN make = 'Nissan' AND model = 'Rogue' THEN 28000
        WHEN make = 'Mazda' THEN 32000
        WHEN make = 'Volkswagen' THEN 32000
        WHEN make = 'Ford' AND model = 'Explorer' THEN 38000
        WHEN make = 'Chevrolet' AND model = 'Equinox' THEN 32000
        ELSE 35000
    END
WHERE estimated_value IS NULL;

-- =============================================================================
-- Summary
-- =============================================================================
SELECT 'Data population completed!' AS status;
SELECT 'Total customers: ' || COUNT(*) FROM customers;
SELECT 'Total policies: ' || COUNT(*) FROM policies;
SELECT 'Total vehicles: ' || COUNT(*) FROM vehicles;
SELECT 'Total drivers: ' || COUNT(*) FROM drivers;
SELECT 'Drivers with phone: ' || COUNT(*) FROM drivers WHERE phone_number IS NOT NULL;
SELECT 'Policies with coverage: ' || COUNT(*) FROM policies WHERE has_comprehensive IS NOT NULL;
