# Communications Agent

**Port:** 8085
**Implementation:** Real APIs + Simulated

The Communications agent handles all driver and adjuster communications including SMS notifications, email reports, push notifications, and roadside assistance dispatch. It integrates with Twilio for SMS and Gmail SMTP for email when configured.

## Integration Status

| Feature | Provider | Status |
|---------|----------|--------|
| SMS Notifications | Twilio | Real (when configured) |
| Email Reports | Gmail SMTP | Real (when configured) |
| Push Notifications | - | Simulated |
| Roadside Dispatch | - | Simulated |

## MCP Tools

### `sendSms`

Send an SMS message to the driver checking on their welfare.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `phoneNumber` | String | Driver's phone number |
| `message` | String | Message content |
| `claimReference` | String | Claim reference for tracking |

**Returns:** `SmsResult` containing:
- `sent` - Whether message was sent successfully
- `sentAt` - Timestamp of send attempt
- `messageId` - Twilio message SID or simulation ID
- `status` - Delivery status (SENT, SIMULATED, FAILED)

### `sendPushNotification`

Send a push notification through the safe driver app.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `driverId` | int | Driver ID for push routing |
| `title` | String | Notification title |
| `body` | String | Notification body |
| `claimReference` | String | Claim reference for tracking |

**Returns:** `PushResult` with send confirmation.

### `notifyAdjuster`

Notify a claims adjuster about a new or updated claim.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `claimNumber` | String | Claim number |
| `severity` | String | MINOR, MODERATE, or SEVERE |
| `summary` | String | Full incident report or summary |

**Returns:** `AdjusterNotification` containing:
- `sent` - Whether notification was sent
- `assignedAdjuster` - Name of assigned adjuster
- `priority` - Claim priority (HIGH, MEDIUM, LOW)
- `notifiedAt` - Notification timestamp

### `dispatchRoadsideAssistance`

Dispatch roadside assistance to the accident location.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `latitude` | double | Latitude of accident |
| `longitude` | double | Longitude of accident |
| `driverPhone` | String | Driver's phone for callback |
| `claimReference` | String | Claim reference |

**Returns:** `RoadsideDispatch` containing:
- `dispatched` - Whether dispatch was successful
- `dispatchId` - Dispatch reference ID
- `dispatchedAt` - Dispatch timestamp
- `estimatedArrivalMinutes` - ETA in minutes

### `sendFnolEmail`

Send FNOL report email to claims adjuster with full accident details.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `claimReference` | String | Claim reference number |
| `driverName` | String | Driver's full name |
| `policyNumber` | String | Policy number |
| `severity` | String | MINOR, MODERATE, or SEVERE |
| `reportContent` | String | Full FNOL report content |

**Returns:** `EmailResult` containing:
- `sent` - Whether email was sent
- `sentAt` - Send timestamp
- `messageId` - Email message ID
- `status` - Delivery status
- `recipient` - Recipient email address

### `getFullCommunicationsStatus`

Get complete communication status for a claim including driver outreach and all logged communications.

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `claimReference` | String | Claim reference |
| `driverName` | String | Driver name |
| `driverPhone` | String | Driver phone number |
| `severity` | String | Accident severity |

**Returns:** `CommunicationsStatus` containing:
- `driverOutreach` - SMS/push notification status
- `adjusterNotified` - Whether adjuster was notified
- `assignedAdjuster` - Assigned adjuster name
- `roadsideDispatched` - Whether roadside was dispatched
- `communicationLogs` - Full audit trail

## Adjuster Assignment

| Severity | Adjuster | Priority |
|----------|----------|----------|
| **SEVERE** | Sarah Martinez (Senior Adjuster) | HIGH |
| **MODERATE** | Mike Johnson (Adjuster) | MEDIUM |
| **MINOR** | Mike Johnson (Adjuster) | LOW |

## Configuration

### Twilio SMS

```yaml
# Environment variables or vars.yaml
TWILIO_ACCOUNT_SID: ACxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN: your_auth_token
TWILIO_FROM_NUMBER: +1234567890
```

### Gmail SMTP

```yaml
# Environment variables or vars.yaml
GMAIL_USERNAME: your-email@gmail.com
GMAIL_APP_PASSWORD: your_app_password  # Use App Password, not account password
GMAIL_ADJUSTER_EMAIL: adjuster@company.com
```

To create a Gmail App Password:
1. Enable 2-Factor Authentication on your Google account
2. Go to Google Account > Security > App passwords
3. Generate a new app password for "Mail"

## Email Templates

### FNOL Report Email

The agent sends professionally formatted HTML emails with:
- Color-coded severity banner (red=SEVERE, yellow=MODERATE, green=MINOR)
- Claim metadata (reference, driver, policy, timestamp)
- Full incident report with pre-formatted content
- CRASH system footer

### Sample Email

```html
FNOL Claim Notification
[HIGH PRIORITY]

Claim Number: CLM-2025-001234
Severity: SEVERE
Assigned To: Sarah Martinez (Senior Adjuster)
Notification Time: January 8, 2025 at 2:45 PM EST

Incident Details:
[Full FNOL report content...]
```

## Example Usage

```bash
# Send FNOL email via MCP
curl -X POST http://localhost:8085/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "sendFnolEmail",
      "arguments": {
        "claimReference": "CLM-2025-001234",
        "driverName": "Jane Smith",
        "policyNumber": "POL-200018",
        "severity": "MODERATE",
        "reportContent": "Full report content here..."
      }
    }
  }'
```

## Communication Audit Trail

All communications are logged with:
- Timestamp
- Type (SMS, EMAIL, PUSH, DISPATCH, INTERNAL)
- Direction (OUTBOUND, INBOUND)
- Recipient/party
- Summary
- Success status

## Health Check

```bash
curl http://localhost:8085/actuator/health
```

## Dependencies

- Spring Boot 3.5.x
- Spring AI MCP Server
- Twilio SDK (com.twilio.sdk:twilio)
- Spring Boot Mail (jakarta.mail)
- crash-domain (shared domain objects)

## Future Enhancements

- Real push notification integration (Firebase/APNs)
- Real roadside assistance dispatch API
- Two-way SMS conversation handling
- Voice call integration
- Multi-language support
