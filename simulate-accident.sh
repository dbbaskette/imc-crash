#!/usr/bin/env bash
#
# Simulate an accident event by publishing to RabbitMQ
# Usage: ./simulate-accident.sh [accident_type] [severity]
#   accident_type: rollover, head_on, t_bone, single_vehicle, rear_end_collision,
#                  rear_ended, side_swipe, multi_vehicle_pileup, hit_and_run (default: frontal)
#   severity: minor, moderate, severe (default: moderate)
#
# Examples:
#   ./simulate-accident.sh t_bone severe       # T-bone side impact, severe
#   ./simulate-accident.sh rollover            # Rollover, moderate severity
#   ./simulate-accident.sh                     # Default frontal collision, moderate
#

set -e

# Configuration
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
RABBITMQ_USER="${RABBITMQ_USER:-guest}"
RABBITMQ_PASS="${RABBITMQ_PASS:-guest}"

# Exchange matching telematics generator
EXCHANGE="telematics_exchange"
ROUTING_KEY=""

# Parse arguments
ACCIDENT_TYPE="${1:-frontal}"
SEVERITY="${2:-moderate}"

# Generate timestamps
EVENT_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Map simplified names to full accident type names
case "$ACCIDENT_TYPE" in
  rollover|ROLLOVER)
    ACCIDENT_TYPE_FULL="ROLLOVER"
    ;;
  head_on|HEAD_ON|headon)
    ACCIDENT_TYPE_FULL="HEAD_ON"
    ;;
  t_bone|T_BONE|tbone|side)
    ACCIDENT_TYPE_FULL="T_BONE"
    ;;
  single|single_vehicle|SINGLE_VEHICLE)
    ACCIDENT_TYPE_FULL="SINGLE_VEHICLE"
    ;;
  rear_end|rear_end_collision|REAR_END_COLLISION)
    ACCIDENT_TYPE_FULL="REAR_END_COLLISION"
    ;;
  rear_ended|REAR_ENDED|rearended)
    ACCIDENT_TYPE_FULL="REAR_ENDED"
    ;;
  side_swipe|SIDE_SWIPE|sideswipe)
    ACCIDENT_TYPE_FULL="SIDE_SWIPE"
    ;;
  multi|multi_vehicle|pileup|MULTI_VEHICLE_PILEUP)
    ACCIDENT_TYPE_FULL="MULTI_VEHICLE_PILEUP"
    ;;
  hit_and_run|HIT_AND_RUN|hitandrun)
    ACCIDENT_TYPE_FULL="HIT_AND_RUN"
    ;;
  frontal|front|*)
    ACCIDENT_TYPE_FULL="FRONTAL"
    ACCIDENT_TYPE="frontal"
    ;;
esac

# Severity multiplier
case "$SEVERITY" in
  minor)
    MULTIPLIER=0.5
    POLICY_ID=200020
    ;;
  severe)
    MULTIPLIER=1.3
    POLICY_ID=200019
    ;;
  moderate|*)
    MULTIPLIER=1.0
    POLICY_ID=200018
    ;;
esac

# Set sensor values based on accident type (matching ImpactAnalystService logic)
# These profiles are designed to trigger specific impact type detection
case "$ACCIDENT_TYPE_FULL" in
  ROLLOVER)
    # Extreme Z-axis, chaotic rotation
    BASE_G_FORCE=7.5
    ACCEL_X=$(awk "BEGIN {print -2.5 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 1.8 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 7.2 * $MULTIPLIER}")  # Dominant Z-axis
    GYRO_X=120.0
    GYRO_Y=95.0
    GYRO_Z=150.0
    SPEED_MPH=45
    EXPECTED_IMPACT="ROLLOVER"
    ;;
  HEAD_ON)
    # Extreme negative X (most extreme frontal)
    BASE_G_FORCE=9.0
    ACCEL_X=$(awk "BEGIN {print -10.5 * $MULTIPLIER}")  # Extreme negative
    ACCEL_Y=$(awk "BEGIN {print 0.8 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 1.2 * $MULTIPLIER}")
    GYRO_X=75.0
    GYRO_Y=40.0
    GYRO_Z=55.0
    SPEED_MPH=65
    EXPECTED_IMPACT="FRONTAL"
    ;;
  T_BONE)
    # Strong lateral Y-axis (perpendicular side impact)
    BASE_G_FORCE=7.8
    ACCEL_X=$(awk "BEGIN {print -1.5 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 7.2 * $MULTIPLIER}")  # Dominant Y-axis
    ACCEL_Z=$(awk "BEGIN {print 0.9 * $MULTIPLIER}")
    GYRO_X=65.0
    GYRO_Y=85.0
    GYRO_Z=45.0
    SPEED_MPH=0  # Often stationary when hit
    EXPECTED_IMPACT="SIDE"
    ;;
  SINGLE_VEHICLE)
    # Strong negative X (hitting fixed object)
    BASE_G_FORCE=6.5
    ACCEL_X=$(awk "BEGIN {print -7.0 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 0.5 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 0.8 * $MULTIPLIER}")
    GYRO_X=50.0
    GYRO_Y=25.0
    GYRO_Z=35.0
    SPEED_MPH=40
    EXPECTED_IMPACT="FRONTAL"
    ;;
  REAR_END_COLLISION)
    # Moderate negative X (hitting vehicle ahead)
    BASE_G_FORCE=5.5
    ACCEL_X=$(awk "BEGIN {print -5.8 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 0.4 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 0.6 * $MULTIPLIER}")
    GYRO_X=35.0
    GYRO_Y=20.0
    GYRO_Z=28.0
    SPEED_MPH=35
    EXPECTED_IMPACT="FRONTAL"
    ;;
  REAR_ENDED)
    # Positive X (pushed forward)
    BASE_G_FORCE=4.2
    ACCEL_X=$(awk "BEGIN {print 4.5 * $MULTIPLIER}")  # Positive = forward jolt
    ACCEL_Y=$(awk "BEGIN {print 0.3 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 0.5 * $MULTIPLIER}")
    GYRO_X=25.0
    GYRO_Y=15.0
    GYRO_Z=20.0
    SPEED_MPH=15
    EXPECTED_IMPACT="REAR"
    ;;
  SIDE_SWIPE)
    # Moderate lateral Y (glancing side impact)
    BASE_G_FORCE=3.2
    ACCEL_X=$(awk "BEGIN {print -0.8 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 2.8 * $MULTIPLIER}")  # Moderate Y
    ACCEL_Z=$(awk "BEGIN {print 0.4 * $MULTIPLIER}")
    GYRO_X=18.0
    GYRO_Y=30.0
    GYRO_Z=22.0
    SPEED_MPH=30
    EXPECTED_IMPACT="SIDE"
    ;;
  MULTI_VEHICLE_PILEUP)
    # Varied forces, multiple impacts
    BASE_G_FORCE=5.8
    ACCEL_X=$(awk "BEGIN {print -4.2 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 2.1 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 1.5 * $MULTIPLIER}")
    GYRO_X=55.0
    GYRO_Y=45.0
    GYRO_Z=60.0
    SPEED_MPH=28
    EXPECTED_IMPACT="FRONTAL"
    ;;
  HIT_AND_RUN)
    # Varied, moderate forces
    BASE_G_FORCE=4.5
    ACCEL_X=$(awk "BEGIN {print -3.2 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 1.8 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 0.9 * $MULTIPLIER}")
    GYRO_X=40.0
    GYRO_Y=35.0
    GYRO_Z=38.0
    SPEED_MPH=25
    EXPECTED_IMPACT="FRONTAL"
    ;;
  FRONTAL|*)
    # Generic frontal collision (moderate negative X)
    BASE_G_FORCE=4.8
    ACCEL_X=$(awk "BEGIN {print -4.2 * $MULTIPLIER}")
    ACCEL_Y=$(awk "BEGIN {print 0.6 * $MULTIPLIER}")
    ACCEL_Z=$(awk "BEGIN {print 0.8 * $MULTIPLIER}")
    GYRO_X=38.0
    GYRO_Y=22.0
    GYRO_Z=30.0
    SPEED_MPH=35
    EXPECTED_IMPACT="FRONTAL"
    ;;
esac

# Calculate final g-force
G_FORCE=$(awk "BEGIN {print $BASE_G_FORCE * $MULTIPLIER}")

# Generate random IDs for variety
VEHICLE_ID=$((300000 + RANDOM % 1000))
DRIVER_ID=$((400000 + RANDOM % 1000))

# Create telemetry JSON matching telematics generator schema
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
  "gps_latitude": 33.7490,
  "gps_longitude": -84.3880,
  "current_street": "Peachtree St NE, Atlanta",
  "accelerometer_x": ${ACCEL_X},
  "accelerometer_y": ${ACCEL_Y},
  "accelerometer_z": ${ACCEL_Z},
  "gyroscope_x": ${GYRO_X},
  "gyroscope_y": ${GYRO_Y},
  "gyroscope_z": ${GYRO_Z},
  "device_battery_level": 78,
  "device_signal_strength": -65,
  "heading_degrees": 180.5,
  "gps_altitude": 320.5,
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
  "hazard_lights_on": true,
  "accident_type": "${ACCIDENT_TYPE_FULL}"
}
EOF

SEVERITY_UPPER=$(echo "$SEVERITY" | tr '[:lower:]' '[:upper:]')

echo "=============================================="
echo "  CRASH Accident Simulator - Enhanced"
echo "=============================================="
echo "Accident Type:      ${ACCIDENT_TYPE_FULL}"
echo "Severity:           ${SEVERITY_UPPER}"
echo "Expected Impact:    ${EXPECTED_IMPACT}"
echo "----------------------------------------------"
echo "Policy ID:          ${POLICY_ID}"
echo "G-Force:            ${G_FORCE}"
echo "Speed:              ${SPEED_MPH} mph"
echo "Accelerometer:"
echo "  X (front/back):   ${ACCEL_X}g"
echo "  Y (left/right):   ${ACCEL_Y}g"
echo "  Z (up/down):      ${ACCEL_Z}g"
echo "Event Time:         ${EVENT_TIME}"
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
    -d '{"type":"fanout","durable":true}' \
    "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/exchanges/%2f/${EXCHANGE}" > /dev/null

# Publish message to RabbitMQ using management API
echo "Publishing accident event to RabbitMQ..."

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
        echo "SUCCESS: Message published and routed to consumer(s)"
        echo ""
        echo "✅ Accident simulation complete!"
        echo "   Check crash-sink logs or database for FNOL report"
        echo "   Expected impact type: ${EXPECTED_IMPACT}"
    else
        echo "WARNING: Message published but not routed to any queue"
        echo "         (No consumer bound to exchange yet)"
    fi
else
    echo "ERROR: Failed to publish message (HTTP ${HTTP_CODE})"
    echo "$BODY"
    exit 1
fi

echo "=============================================="
