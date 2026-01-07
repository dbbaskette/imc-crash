#!/usr/bin/env bash
#
# Simulate an accident event by publishing to RabbitMQ
# Usage: ./simulate-accident.sh [severity]
#   severity: minor, moderate, severe (default: moderate)
#

set -e

# Configuration
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
RABBITMQ_USER="${RABBITMQ_USER:-guest}"
RABBITMQ_PASS="${RABBITMQ_PASS:-guest}"

# Queue name matching the tap from telemetry processor
EXCHANGE="telematics_exchange"
ROUTING_KEY=""

# Parse severity argument
SEVERITY="${1:-moderate}"

# Generate timestamps
EVENT_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Set values based on severity
case "$SEVERITY" in
  minor)
    G_FORCE=2.8
    SPEED_MPH=15
    ACCEL_X=-1.5
    ACCEL_Y=0.2
    ACCEL_Z=0.4
    GYRO_X=10.0
    GYRO_Y=5.0
    GYRO_Z=8.0
    POLICY_ID=200020
    ;;
  severe)
    G_FORCE=6.2
    SPEED_MPH=55
    ACCEL_X=-4.5
    ACCEL_Y=1.2
    ACCEL_Z=2.1
    GYRO_X=85.0
    GYRO_Y=45.0
    GYRO_Z=60.0
    POLICY_ID=200019
    ;;
  moderate|*)
    G_FORCE=3.8
    SPEED_MPH=34.5
    ACCEL_X=-2.1
    ACCEL_Y=0.3
    ACCEL_Z=0.8
    GYRO_X=25.0
    GYRO_Y=15.0
    GYRO_Z=20.0
    POLICY_ID=200018
    ;;
esac

# Generate random IDs for variety
VEHICLE_ID=$((300000 + RANDOM % 1000))
DRIVER_ID=$((400000 + RANDOM % 1000))

# Create telemetry JSON matching the 35-field flat schema from telemetry processor
# This matches what the telemetry processor outputs after filtering
read -r -d '' TELEMETRY_JSON << EOF || true
{
  "policy_id": ${POLICY_ID},
  "vehicle_id": ${VEHICLE_ID},
  "driver_id": ${DRIVER_ID},
  "vin": "1HGBH41JXMN$(printf '%06d' $((RANDOM % 999999)))",
  "event_time": "${EVENT_TIME}",
  "speed_mph": ${SPEED_MPH},
  "speed_limit_mph": 35,
  "g_force": ${G_FORCE},
  "gps_latitude": 39.1157,
  "gps_longitude": -77.5636,
  "current_street": "Main Street",
  "accelerometer_x": ${ACCEL_X},
  "accelerometer_y": ${ACCEL_Y},
  "accelerometer_z": ${ACCEL_Z},
  "gyroscope_x": ${GYRO_X},
  "gyroscope_y": ${GYRO_Y},
  "gyroscope_z": ${GYRO_Z},
  "device_battery_level": 78,
  "device_signal_strength": -65,
  "heading_degrees": 180.5,
  "altitude_meters": 125.3,
  "gps_accuracy_meters": 4.2,
  "engine_rpm": 0,
  "fuel_level_percent": 62,
  "odometer_miles": 45678,
  "ambient_temp_celsius": 18.5,
  "tire_pressure_fl_psi": 32.1,
  "tire_pressure_fr_psi": 32.3,
  "tire_pressure_rl_psi": 31.8,
  "tire_pressure_rr_psi": 32.0,
  "abs_engaged": true,
  "airbag_deployed": false,
  "seatbelt_fastened": true,
  "doors_locked": true,
  "hazard_lights_on": true
}
EOF

SEVERITY_UPPER=$(echo "$SEVERITY" | tr '[:lower:]' '[:upper:]')

echo "=============================================="
echo "  CRASH Accident Simulator"
echo "=============================================="
echo "Severity:    ${SEVERITY_UPPER}"
echo "Policy ID:   ${POLICY_ID}"
echo "G-Force:     ${G_FORCE}"
echo "Speed:       ${SPEED_MPH} mph"
echo "Event Time:  ${EVENT_TIME}"
echo "----------------------------------------------"

# Check if RabbitMQ management API is available
if ! curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/overview" > /dev/null 2>&1; then
    echo "ERROR: Cannot connect to RabbitMQ management API at ${RABBITMQ_HOST}:${RABBITMQ_PORT}"
    echo "Make sure RabbitMQ is running with management plugin enabled."
    echo ""
    echo "Start with: docker-compose up -d rabbitmq"
    exit 1
fi

# First, ensure the exchange exists (create if needed)
echo "Ensuring exchange '${EXCHANGE}' exists..."
curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" \
    -X PUT \
    -H "Content-Type: application/json" \
    -d '{"type":"topic","durable":true}' \
    "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/exchanges/%2f/${EXCHANGE}" > /dev/null

# Publish message to RabbitMQ using management API
echo "Publishing accident event to RabbitMQ..."

# Escape JSON for the payload wrapper
ESCAPED_JSON=$(echo "$TELEMETRY_JSON" | jq -c '.')

# Create the publish payload
PUBLISH_PAYLOAD=$(cat << PAYLOAD
{
  "properties": {
    "content_type": "application/json",
    "delivery_mode": 2
  },
  "routing_key": "",
  "payload": $(echo "$TELEMETRY_JSON" | jq -c '.' | jq -R '.'),
  "payload_encoding": "string"
}
PAYLOAD
)

RESPONSE=$(curl -s -w "\n%{http_code}" -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$PUBLISH_PAYLOAD" \
    "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/exchanges/%2f/${EXCHANGE}/publish")

HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    ROUTED=$(echo "$BODY" | jq -r '.routed // false')
    if [ "$ROUTED" = "true" ]; then
        echo "SUCCESS: Message published and routed to queue(s)"
    else
        echo "WARNING: Message published but not routed to any queue"
        echo "         (No consumer bound to exchange yet - this is OK for testing)"
    fi
else
    echo "ERROR: Failed to publish message (HTTP ${HTTP_CODE})"
    echo "$BODY"
    exit 1
fi

echo "----------------------------------------------"
echo "Message payload:"
echo "$TELEMETRY_JSON" | jq '.'
echo "=============================================="
