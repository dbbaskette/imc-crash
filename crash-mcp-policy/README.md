# Policy Agent

**Port:** 8083
**Implementation:** Simulated

The Policy agent retrieves insurance policy information, driver profiles, and vehicle details. In production, this would connect to the insurance company's policy management system.

## MCP Tools

### `lookupPolicy`

Look up insurance policy details including coverage types, deductible, and benefits.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `policyId` | int | Insurance policy ID |

**Returns:** `Policy` containing:
- `policyNumber` - Policy identifier (e.g., "POL-200018")
- `status` - Policy status (ACTIVE, SUSPENDED, etc.)
- `coverageTypes` - List of coverage types
- `deductible` - Deductible amount in dollars
- `roadsideAssistance` - Whether roadside assistance is included
- `rentalCoverage` - Whether rental car coverage is included

### `getDriverProfile`

Get driver profile including contact information and risk score.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `driverId` | int | Driver ID |
| `policyId` | int | Associated policy ID |

**Returns:** `Driver` containing:
- `name` - Driver's full name
- `phone` - Contact phone number
- `email` - Email address
- `riskScore` - Driver risk score (0-100)
- `emergencyContactName` - Emergency contact name
- `emergencyContactPhone` - Emergency contact phone

### `getVehicleDetails`

Get vehicle details including make, model, year, and estimated value.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `vehicleId` | int | Vehicle ID |
| `vin` | String | Vehicle Identification Number |
| `policyId` | int | Associated policy ID |

**Returns:** `Vehicle` containing:
- `year` - Model year
- `make` - Manufacturer (Honda, Toyota, etc.)
- `model` - Model name
- `color` - Vehicle color
- `vin` - VIN
- `licensePlate` - License plate number
- `estimatedValue` - Current estimated value

### `getFullPolicyInfo`

Get complete policy information in one call.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `policyId` | int | Insurance policy ID |
| `driverId` | int | Driver ID |
| `vehicleId` | int | Vehicle ID |
| `vin` | String | Vehicle Identification Number |

**Returns:** `PolicyInfo` containing policy, driver, and vehicle details.

## Test Policies

Three pre-configured policies are available for testing:

| Policy ID | Driver | Vehicle | Phone |
|-----------|--------|---------|-------|
| 200018 | Jane Smith | 2022 Honda Accord (Silver) | +1-703-555-0123 |
| 200019 | John Doe | 2021 Toyota Camry (Blue) | +1-703-555-0456 |
| 200020 | Sarah Johnson | 2023 Ford F-150 (White) | +1-703-555-0789 |

## Standard Coverage Types

All test policies include:
- Comprehensive
- Collision
- Liability
- Medical Payments
- Uninsured Motorist

## Example Usage

```bash
# Look up policy via MCP
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "getFullPolicyInfo",
      "arguments": {
        "policyId": 200018,
        "driverId": 400018,
        "vehicleId": 300021,
        "vin": "1HGBH41JXMN109186"
      }
    }
  }'
```

## Sample Output

```
Policy Information:
  Policy Number: POL-200018
  Status: ACTIVE
  Coverage: Comprehensive, Collision, Liability, Medical Payments, Uninsured Motorist
  Deductible: $500
  Roadside Assistance: Yes
  Rental Coverage: Yes

Driver:
  Name: Jane Smith
  Phone: +1-703-555-0123
  Email: jane.smith@email.com
  Risk Score: 72

Vehicle:
  2022 Honda Accord (Silver)
  VIN: 1HGBH41JXMN109186
  License: VA-300-4521
  Estimated Value: $24,500
```

## Production Integration

To connect to a real policy management system:

1. Replace the `POLICIES` map with database/API calls
2. Implement proper authentication
3. Add caching for frequently accessed policies
4. Integrate with policy administration system APIs

```java
// Example production implementation
@McpTool(description = "Look up policy details")
public Policy lookupPolicy(int policyId) {
    return policyRepository.findById(policyId)
        .orElseThrow(() -> new PolicyNotFoundException(policyId));
}
```

## Health Check

```bash
curl http://localhost:8083/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- crash-domain (shared domain objects)
