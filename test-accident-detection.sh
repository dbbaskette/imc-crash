#!/usr/bin/env bash
#
# Test all 9 accident types with 3 severity levels (27 total tests)
# This will help identify classification accuracy issues
#

set -e

ACCIDENT_TYPES=(
    "rollover"
    "head_on"
    "t_bone"
    "single_vehicle"
    "rear_end_collision"
    "rear_ended"
    "side_swipe"
    "multi_vehicle_pileup"
    "hit_and_run"
)

SEVERITIES=("minor" "moderate" "severe")

echo "=========================================="
echo "  CRASH Accident Detection Test Suite"
echo "=========================================="
echo "Testing 9 accident types × 3 severities = 27 tests"
echo "Start time: $(date)"
echo ""

TOTAL=0
for ACCIDENT_TYPE in "${ACCIDENT_TYPES[@]}"; do
    for SEVERITY in "${SEVERITIES[@]}"; do
        TOTAL=$((TOTAL + 1))
        echo "[$TOTAL/27] Testing: $ACCIDENT_TYPE - $SEVERITY"
        ./simulate-accident.sh "$ACCIDENT_TYPE" "$SEVERITY" | grep -E "(Accident Type:|Expected Impact:|G-Force:)" | sed 's/^/  /'
        echo "  Waiting 3s for processing..."
        sleep 3
        echo ""
    done
done

echo "=========================================="
echo "All 27 tests completed!"
echo "End time: $(date)"
echo ""
echo "Analyzing results from database..."
echo ""
