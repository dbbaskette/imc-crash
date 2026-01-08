# Services Agent

**Port:** 8084
**Implementation:** Simulated

The Services agent locates nearby service providers relevant to accident response including body shops, tow services, hospitals, and rental car locations. In production, this would integrate with Google Places API or similar services.

## MCP Tools

### `findBodyShops`

Find nearby auto body shops for vehicle repair.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |
| `radiusMiles` | double | Search radius in miles |

**Returns:** List of `ServiceLocation` containing:
- `name` - Business name
- `address` - Full address
- `phone` - Contact phone number
- `distanceMiles` - Distance from accident
- `rating` - Customer rating (1-5)
- `estimatedArrivalMinutes` - null (stationary location)
- `preferredProvider` - Insurance network status
- `currentlyOpen` - Operating status

### `findTowServices`

Find nearby tow truck services.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |
| `radiusMiles` | double | Search radius in miles |

**Returns:** List of `ServiceLocation` with ETA for dispatch.

### `findMedicalFacilities`

Find nearby hospitals and urgent care facilities.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |
| `radiusMiles` | double | Search radius in miles |

**Returns:** List of `ServiceLocation` including trauma center status.

### `findRentalCars`

Find nearby rental car locations for temporary transportation.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |

**Returns:** List of `ServiceLocation` for rental agencies.

### `getAllNearbyServices`

Get all relevant nearby services based on accident severity.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |
| `severity` | String | MINOR, MODERATE, or SEVERE |
| `radiusMiles` | double | Search radius in miles |

**Returns:** `NearbyServices` containing:
- `bodyShops` - List of body shops
- `towServices` - List of tow services
- `medicalFacilities` - List of hospitals (SEVERE only)
- `rentalCars` - List of rental agencies
- `recommendation` - Dispatch recommendation
- `vehicleDrivable` - Whether vehicle can be driven

## Severity-Based Behavior

| Severity | Body Shops | Tow Services | Hospitals | Rental Cars | Recommendation |
|----------|------------|--------------|-----------|-------------|----------------|
| **SEVERE** | Yes | Yes (urgent) | Yes | Yes | "URGENT: Dispatch tow immediately. Medical facilities alerted." |
| **MODERATE** | Yes | Yes | No | Yes | "Tow service recommended - vehicle likely not drivable." |
| **MINOR** | Yes | Yes | No | Yes | "Vehicle may be drivable. Body shop referral provided." |

## Simulated Service Providers

### Body Shops
- Leesburg Auto Body
- Precision Collision Center
- Quick Fix Auto Repair
- Premier Body Works

### Tow Services
- AAA Roadside (preferred)
- Loudoun Towing
- 24/7 Tow Service

### Medical Facilities
- Inova Loudoun Hospital (preferred)
- Reston Hospital Center (preferred)
- Urgent Care Plus

### Rental Cars
- Enterprise Rent-A-Car (preferred partner)
- Hertz
- Budget

## Example Usage

```bash
# Get all nearby services for a moderate accident
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "getAllNearbyServices",
      "arguments": {
        "latitude": 39.1157,
        "longitude": -77.5636,
        "severity": "MODERATE",
        "radiusMiles": 10.0
      }
    }
  }'
```

## Sample Output

```
Nearby Services:
  Recommendation: Tow service recommended - vehicle likely not drivable.
  Vehicle Drivable: No

Body Shops:
  1. Leesburg Auto Body - 1.2 mi - Rating: 4.3 (Preferred)
     120 Main St, Leesburg, VA - 703-555-0142
  2. Precision Collision Center - 2.8 mi - Rating: 4.7
     456 Oak Ave, Leesburg, VA - 703-555-0188

Tow Services:
  1. AAA Roadside - 1.5 mi - ETA: 18 min (Preferred)
     Regional Coverage - 800-222-4357
  2. Loudoun Towing - 2.1 mi - ETA: 25 min
     540 Route 7, Leesburg, VA - 703-555-0199

Rental Cars:
  1. Enterprise Rent-A-Car - 1.8 mi (Preferred Partner)
     567 Market St, Leesburg, VA - 703-555-0155
```

## Production Integration

To connect to real service location APIs:

```java
// Example Google Places API integration
@McpTool(description = "Find nearby body shops")
public List<ServiceLocation> findBodyShops(double lat, double lng, double radius) {
    PlacesSearchRequest request = PlacesApi.nearbySearchQuery(geoApiContext,
        new LatLng(lat, lng))
        .radius((int)(radius * 1609.34))  // Convert miles to meters
        .type(PlaceType.CAR_REPAIR)
        .keyword("auto body collision");

    return Arrays.stream(request.await().results)
        .map(this::toServiceLocation)
        .collect(Collectors.toList());
}
```

## Health Check

```bash
curl http://localhost:8084/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- crash-domain (shared domain objects)

## Future Enhancements

- Google Places API integration for real service locations
- Real-time tow truck availability and dispatch
- Insurance network provider filtering
- Customer review integration
- Wait time estimates for body shops
