#!/bin/bash

# CRASH - Initialize Greenplum/PostgreSQL Database
# Reads connection info from vars.yaml and runs migration/data scripts
#
# Usage:
#   ./init-db.sh              - Run migration + data population (safe, preserves data)
#   ./init-db.sh --full       - Drop and recreate all tables (DESTRUCTIVE!)
#   ./init-db.sh --dry-run    - Show what would be executed without running
#   ./init-db.sh --migrate    - Run only schema migration (add missing columns)
#   ./init-db.sh --data       - Run only data population

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VARS_FILE="${SCRIPT_DIR}/vars.yaml"
SQL_FILE="${SCRIPT_DIR}/db/init-schema.sql"
MIGRATE_FILE="${SCRIPT_DIR}/db/migrate-schema.sql"
DATA_FILE="${SCRIPT_DIR}/db/populate-data.sql"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

DRY_RUN=false
FULL_INIT=false
MIGRATE_ONLY=false
DATA_ONLY=false

for arg in "$@"; do
    case $arg in
        --dry-run)
            DRY_RUN=true
            ;;
        --full)
            FULL_INIT=true
            ;;
        --migrate)
            MIGRATE_ONLY=true
            ;;
        --data)
            DATA_ONLY=true
            ;;
    esac
done

echo -e "${CYAN}"
echo "   ____  ____      _     ____   _   _ "
echo "  / ___||  _ \    / \   / ___| | | | |"
echo " | |    | |_) |  / _ \  \___ \ | |_| |"
echo " | |___ |  _ <  / ___ \  ___) ||  _  |"
echo "  \____||_| \_\/_/   \_\|____/ |_| |_|"
echo ""
echo "  Database Initialization"
echo -e "${NC}"
echo

# Parse YAML value
parse_yaml_value() {
    local file=$1
    local section=$2
    local key=$3

    awk -v section="$section" -v key="$key" '
        BEGIN { in_section = 0 }
        /^[a-zA-Z]/ { in_section = 0 }
        $0 ~ "^" section ":" { in_section = 1; next }
        in_section && $0 ~ "^[[:space:]]+" key ":" {
            gsub(/^[[:space:]]*[^:]+:[[:space:]]*/, "")
            if (match($0, /^"[^"]*"/)) {
                print substr($0, 2, RLENGTH - 2)
            } else if (match($0, /^'"'"'[^'"'"']*'"'"'/)) {
                print substr($0, 2, RLENGTH - 2)
            } else {
                val = $0
                gsub(/[[:space:]]*#.*$/, "", val)
                gsub(/[[:space:]]+$/, "", val)
                print val
            }
            exit
        }
    ' "$file"
}

# Check if vars.yaml exists
if [ ! -f "$VARS_FILE" ]; then
    echo -e "${RED}ERROR: vars.yaml not found${NC}"
    echo "Please create vars.yaml from vars.yaml.template:"
    echo "  cp vars.yaml.template vars.yaml"
    exit 1
fi

# Check if SQL files exist
if [ "$FULL_INIT" = true ] && [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}ERROR: SQL file not found: $SQL_FILE${NC}"
    exit 1
fi
if [ ! -f "$MIGRATE_FILE" ]; then
    echo -e "${RED}ERROR: Migration file not found: $MIGRATE_FILE${NC}"
    exit 1
fi
if [ ! -f "$DATA_FILE" ]; then
    echo -e "${RED}ERROR: Data file not found: $DATA_FILE${NC}"
    exit 1
fi

# Read database configuration from vars.yaml
DB_URL=$(parse_yaml_value "$VARS_FILE" "database" "url")
DB_USER=$(parse_yaml_value "$VARS_FILE" "database" "username")
DB_PASS=$(parse_yaml_value "$VARS_FILE" "database" "password")

if [ -z "$DB_URL" ] || [ -z "$DB_USER" ]; then
    echo -e "${RED}ERROR: Database configuration missing in vars.yaml${NC}"
    echo "Please ensure database.url and database.username are set"
    exit 1
fi

# Parse JDBC URL to extract host, port, database
# Format: jdbc:postgresql://host:port/database
if [[ "$DB_URL" =~ jdbc:postgresql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
else
    echo -e "${RED}ERROR: Could not parse database URL: $DB_URL${NC}"
    echo "Expected format: jdbc:postgresql://host:port/database"
    exit 1
fi

echo -e "${GREEN}✓ Database configuration loaded${NC}"
echo "  Host:     $DB_HOST"
echo "  Port:     $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User:     $DB_USER"
echo

if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}DRY RUN - Would execute:${NC}"
    if [ "$FULL_INIT" = true ]; then
        echo "  PGPASSWORD=*** psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f $SQL_FILE"
        echo -e "${RED}  WARNING: This will DROP all tables and recreate them!${NC}"
    else
        echo "  PGPASSWORD=*** psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f $MIGRATE_FILE"
        echo "  PGPASSWORD=*** psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f $DATA_FILE"
    fi
    exit 0
fi

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo -e "${RED}ERROR: psql command not found${NC}"
    echo "Please install PostgreSQL client tools"
    exit 1
fi

export PGPASSWORD="$DB_PASS"

# Determine which scripts to run
if [ "$FULL_INIT" = true ]; then
    echo -e "${RED}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  WARNING: Full initialization will DROP all tables!         ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════════╝${NC}"
    echo
    read -p "Are you sure you want to continue? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo "Aborted."
        exit 0
    fi
    echo -e "${YELLOW}Running full initialization...${NC}"
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"; then
        echo
        echo -e "${GREEN}✓ Full initialization completed${NC}"
    else
        echo -e "${RED}ERROR: Full initialization failed${NC}"
        exit 1
    fi
elif [ "$MIGRATE_ONLY" = true ]; then
    echo -e "${YELLOW}Running schema migration only...${NC}"
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$MIGRATE_FILE"; then
        echo -e "${GREEN}✓ Schema migration completed${NC}"
    else
        echo -e "${RED}ERROR: Schema migration failed${NC}"
        exit 1
    fi
elif [ "$DATA_ONLY" = true ]; then
    echo -e "${YELLOW}Running data population only...${NC}"
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$DATA_FILE"; then
        echo -e "${GREEN}✓ Data population completed${NC}"
    else
        echo -e "${RED}ERROR: Data population failed${NC}"
        exit 1
    fi
else
    # Default: run migration + data (safe mode)
    echo -e "${YELLOW}Running schema migration...${NC}"
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$MIGRATE_FILE"; then
        echo -e "${GREEN}✓ Schema migration completed${NC}"
    else
        echo -e "${RED}ERROR: Schema migration failed${NC}"
        exit 1
    fi

    echo
    echo -e "${YELLOW}Running data population...${NC}"
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$DATA_FILE"; then
        echo -e "${GREEN}✓ Data population completed${NC}"
    else
        echo -e "${RED}ERROR: Data population failed${NC}"
        exit 1
    fi
fi

echo
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║      Database setup completed successfully!                 ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo
