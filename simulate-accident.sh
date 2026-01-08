#!/usr/bin/env bash
#
# Simulate accident events by publishing to RabbitMQ
#
# Usage: ./simulate-accident.sh [options] [accident_type] [severity]
#
# Options:
#   --num N       Simulate N accidents (default: 1)
#   --random      Use random accident types and severities
#   --delay N     Delay N seconds between accidents (default: 2)
#   --help        Show this help message
#
# Arguments:
#   accident_type: rollover, head_on, t_bone, single_vehicle, rear_end_collision,
#                  rear_ended, side_swipe, multi_vehicle_pileup, hit_and_run (default: frontal)
#   severity:      minor, moderate, severe (default: moderate)
#
# Examples:
#   ./simulate-accident.sh t_bone severe          # Single T-bone, severe
#   ./simulate-accident.sh --num 5 --random       # 5 random accidents
#   ./simulate-accident.sh --num 3 rollover       # 3 rollover accidents, moderate severity
#   ./simulate-accident.sh --num 5 --random --delay 5  # 5 random with 5s delay
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

# Default options
NUM_ACCIDENTS=1
RANDOM_MODE=false
DELAY_SECONDS=2
ACCIDENT_TYPE=""
SEVERITY=""

# Available types and severities for random mode
ACCIDENT_TYPES=("frontal" "rollover" "head_on" "t_bone" "single_vehicle" "rear_end_collision" "rear_ended" "side_swipe" "multi_vehicle_pileup" "hit_and_run")
SEVERITIES=("minor" "moderate" "severe")

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --num)
            NUM_ACCIDENTS="$2"
            shift 2
            ;;
        --random)
            RANDOM_MODE=true
            shift
            ;;
        --delay)
            DELAY_SECONDS="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: ./simulate-accident.sh [options] [accident_type] [severity]"
            echo ""
            echo "Options:"
            echo "  --num N       Simulate N accidents (default: 1)"
            echo "  --random      Use random accident types and severities"
            echo "  --delay N     Delay N seconds between accidents (default: 2)"
            echo "  --help        Show this help message"
            echo ""
            echo "Accident types: frontal, rollover, head_on, t_bone, single_vehicle,"
            echo "                rear_end_collision, rear_ended, side_swipe,"
            echo "                multi_vehicle_pileup, hit_and_run"
            echo ""
            echo "Severities: minor, moderate, severe"
            echo ""
            echo "Examples:"
            echo "  ./simulate-accident.sh t_bone severe          # Single T-bone, severe"
            echo "  ./simulate-accident.sh --num 5 --random       # 5 random accidents"
            echo "  ./simulate-accident.sh --num 3 rollover       # 3 rollover accidents"
            exit 0
            ;;
        -*)
            echo "Unknown option: $1"
            exit 1
            ;;
        *)
            # Positional arguments
            if [ -z "$ACCIDENT_TYPE" ]; then
                ACCIDENT_TYPE="$1"
            elif [ -z "$SEVERITY" ]; then
                SEVERITY="$1"
            fi
            shift
            ;;
    esac
done

# Set defaults for positional args if not provided
ACCIDENT_TYPE="${ACCIDENT_TYPE:-frontal}"
SEVERITY="${SEVERITY:-moderate}"

# Function to get random element from array
random_element() {
    local arr=("$@")
    echo "${arr[RANDOM % ${#arr[@]}]}"
}

# Function to simulate a single accident
simulate_accident() {
    local acc_type="$1"
    local sev="$2"
    local accident_num="$3"

    # Generate timestamps
    EVENT_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Map simplified names to full accident type names
    case "$acc_type" in
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
        acc_type="frontal"
        ;;
    esac

    # Severity multiplier
    case "$sev" in
      minor)
        MULTIPLIER=0.5
        POLICY_ID=$((200010 + RANDOM % 10))
        ;;
      severe)
        MULTIPLIER=1.3
        POLICY_ID=$((200000 + RANDOM % 10))
        ;;
      moderate|*)
        MULTIPLIER=1.0
        POLICY_ID=$((200005 + RANDOM % 10))
        ;;
    esac

    # Set sensor values based on accident type
    case "$ACCIDENT_TYPE_FULL" in
      ROLLOVER)
        BASE_G_FORCE=7.5
        ACCEL_X=$(awk "BEGIN {print -2.5 * $MULTIPLIER}")
        ACCEL_Y=$(awk "BEGIN {print 1.8 * $MULTIPLIER}")
        ACCEL_Z=$(awk "BEGIN {print 7.2 * $MULTIPLIER}")
        GYRO_X=120.0
        GYRO_Y=95.0
        GYRO_Z=150.0
        SPEED_MPH=45
        EXPECTED_IMPACT="ROLLOVER"
        ;;
      HEAD_ON)
        BASE_G_FORCE=9.0
        ACCEL_X=$(awk "BEGIN {print -10.5 * $MULTIPLIER}")
        ACCEL_Y=$(awk "BEGIN {print 0.8 * $MULTIPLIER}")
        ACCEL_Z=$(awk "BEGIN {print 1.2 * $MULTIPLIER}")
        GYRO_X=75.0
        GYRO_Y=40.0
        GYRO_Z=55.0
        SPEED_MPH=65
        EXPECTED_IMPACT="FRONTAL"
        ;;
      T_BONE)
        BASE_G_FORCE=7.8
        ACCEL_X=$(awk "BEGIN {print -1.5 * $MULTIPLIER}")
        ACCEL_Y=$(awk "BEGIN {print 7.2 * $MULTIPLIER}")
        ACCEL_Z=$(awk "BEGIN {print 0.9 * $MULTIPLIER}")
        GYRO_X=65.0
        GYRO_Y=85.0
        GYRO_Z=45.0
        SPEED_MPH=0
        EXPECTED_IMPACT="SIDE"
        ;;
      SINGLE_VEHICLE)
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
        BASE_G_FORCE=4.2
        ACCEL_X=$(awk "BEGIN {print 4.5 * $MULTIPLIER}")
        ACCEL_Y=$(awk "BEGIN {print 0.3 * $MULTIPLIER}")
        ACCEL_Z=$(awk "BEGIN {print 0.5 * $MULTIPLIER}")
        GYRO_X=25.0
        GYRO_Y=15.0
        GYRO_Z=20.0
        SPEED_MPH=15
        EXPECTED_IMPACT="REAR"
        ;;
      SIDE_SWIPE)
        BASE_G_FORCE=3.2
        ACCEL_X=$(awk "BEGIN {print -0.8 * $MULTIPLIER}")
        ACCEL_Y=$(awk "BEGIN {print 2.8 * $MULTIPLIER}")
        ACCEL_Z=$(awk "BEGIN {print 0.4 * $MULTIPLIER}")
        GYRO_X=18.0
        GYRO_Y=30.0
        GYRO_Z=22.0
        SPEED_MPH=30
        EXPECTED_IMPACT="SIDE"
        ;;
      MULTI_VEHICLE_PILEUP)
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

    # Random GPS coordinates in Atlanta area
    LAT=$(awk "BEGIN {printf \"%.4f\", 33.7 + (RANDOM/32768) * 0.1}")
    LON=$(awk "BEGIN {printf \"%.4f\", -84.4 + (RANDOM/32768) * 0.1}")

    # Create telemetry JSON
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
  "gps_latitude": ${LAT},
  "gps_longitude": ${LON},
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

    SEVERITY_UPPER=$(echo "$sev" | tr '[:lower:]' '[:upper:]')

    echo "----------------------------------------------"
    echo "  Accident #${accident_num}"
    echo "----------------------------------------------"
    echo "Type:     ${ACCIDENT_TYPE_FULL} (${EXPECTED_IMPACT})"
    echo "Severity: ${SEVERITY_UPPER}"
    echo "G-Force:  ${G_FORCE}"
    echo "Policy:   ${POLICY_ID}"

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
            echo "Status:   SENT"
        else
            echo "Status:   WARNING - Not routed (no consumer)"
        fi
    else
        echo "Status:   ERROR (HTTP ${HTTP_CODE})"
    fi
}

# Main execution
echo "=============================================="
echo "  CRASH Accident Simulator"
echo "=============================================="
echo "Mode: $([ "$RANDOM_MODE" = true ] && echo "RANDOM" || echo "FIXED")"
echo "Count: ${NUM_ACCIDENTS}"
if [ "$RANDOM_MODE" = false ]; then
    echo "Type: ${ACCIDENT_TYPE}"
    echo "Severity: ${SEVERITY}"
fi
echo "Delay: ${DELAY_SECONDS}s between accidents"

# Check RabbitMQ connection
if ! curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/overview" > /dev/null 2>&1; then
    echo ""
    echo "ERROR: Cannot connect to RabbitMQ at ${RABBITMQ_HOST}:${RABBITMQ_PORT}"
    echo "Start with: docker-compose up -d rabbitmq"
    exit 1
fi

# Ensure exchange exists
curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" \
    -X PUT \
    -H "Content-Type: application/json" \
    -d '{"type":"fanout","durable":true}' \
    "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/exchanges/%2f/${EXCHANGE}" > /dev/null

# Simulate accidents
for ((i=1; i<=NUM_ACCIDENTS; i++)); do
    if [ "$RANDOM_MODE" = true ]; then
        curr_type=$(random_element "${ACCIDENT_TYPES[@]}")
        curr_sev=$(random_element "${SEVERITIES[@]}")
    else
        curr_type="$ACCIDENT_TYPE"
        curr_sev="$SEVERITY"
    fi

    simulate_accident "$curr_type" "$curr_sev" "$i"

    # Delay between accidents (except after the last one)
    if [ "$i" -lt "$NUM_ACCIDENTS" ]; then
        sleep "$DELAY_SECONDS"
    fi
done

echo ""
echo "=============================================="
echo "  Complete! Simulated ${NUM_ACCIDENTS} accident(s)"
echo "=============================================="
echo "Check logs: docker logs imc-crash-crash-sink-1 -f"
echo "Check email: ${GMAIL_ADJUSTER_EMAIL:-imc.adjuster01@gmail.com}"
