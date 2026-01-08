#!/bin/bash

# CRASH - Claims Response Agent System Hive
# Startup Script (Docker Compose)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VARS_FILE="${SCRIPT_DIR}/vars.yaml"
ENV_FILE="${SCRIPT_DIR}/.env"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}"
echo "   ____  ____      _     ____   _   _ "
echo "  / ___||  _ \    / \   / ___| | | | |"
echo " | |    | |_) |  / _ \  \___ \ | |_| |"
echo " | |___ |  _ <  / ___ \  ___) ||  _  |"
echo "  \____||_| \_\/_/   \_\|____/ |_| |_|"
echo ""
echo "  Claims Response Agent System Hive"
echo -e "${NC}"
echo

# Parse YAML value - handles nested keys like "openai.api_key"
parse_yaml_value() {
    local file=$1
    local section=$2
    local key=$3

    # Use awk to parse YAML - find section then find key within it
    awk -v section="$section" -v key="$key" '
        BEGIN { in_section = 0 }
        /^[a-zA-Z]/ { in_section = 0 }
        $0 ~ "^" section ":" { in_section = 1; next }
        in_section && $0 ~ "^[[:space:]]+" key ":" {
            # Extract value after the key
            gsub(/^[[:space:]]*[^:]+:[[:space:]]*/, "")
            # Handle quoted strings - extract content between quotes
            if (match($0, /^"[^"]*"/)) {
                print substr($0, 2, RLENGTH - 2)
            } else if (match($0, /^'"'"'[^'"'"']*'"'"'/)) {
                print substr($0, 2, RLENGTH - 2)
            } else {
                # Unquoted - take until comment or end
                val = $0
                gsub(/[[:space:]]*#.*$/, "", val)
                gsub(/[[:space:]]+$/, "", val)
                print val
            }
            exit
        }
    ' "$file"
}

# Load configuration from vars.yaml
if [ -f "$VARS_FILE" ]; then
    echo -e "${GREEN}✓ Loading configuration from vars.yaml${NC}"

    # Start fresh .env file
    echo "# Auto-generated from vars.yaml - do not edit directly" > "$ENV_FILE"
    echo "# Generated at: $(date)" >> "$ENV_FILE"
    echo "" >> "$ENV_FILE"

    # OpenAI Configuration
    OPENAI_API_KEY=$(parse_yaml_value "$VARS_FILE" "openai" "api_key")
    if [ -n "$OPENAI_API_KEY" ] && [ "$OPENAI_API_KEY" != "sk-your-openai-api-key-here" ]; then
        echo "OPENAI_API_KEY=${OPENAI_API_KEY}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} OPENAI_API_KEY"
    fi

    # Anthropic Configuration
    ANTHROPIC_API_KEY=$(parse_yaml_value "$VARS_FILE" "anthropic" "api_key")
    if [ -n "$ANTHROPIC_API_KEY" ]; then
        echo "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} ANTHROPIC_API_KEY"
    fi

    # Google Gemini Configuration
    GOOGLE_API_KEY=$(parse_yaml_value "$VARS_FILE" "google" "api_key")
    GOOGLE_MODEL=$(parse_yaml_value "$VARS_FILE" "google" "model")
    if [ -n "$GOOGLE_API_KEY" ] && [ "$GOOGLE_API_KEY" != "YOUR_GOOGLE_API_KEY" ]; then
        echo "" >> "$ENV_FILE"
        echo "# Google Gemini Configuration" >> "$ENV_FILE"
        echo "GOOGLE_API_KEY=${GOOGLE_API_KEY}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} GOOGLE_API_KEY"
        if [ -n "$GOOGLE_MODEL" ]; then
            echo "GOOGLE_MODEL=${GOOGLE_MODEL}" >> "$ENV_FILE"
            echo -e "  ${GREEN}✓${NC} GOOGLE_MODEL (${GOOGLE_MODEL})"
        fi
    fi

    # Twilio Configuration
    TWILIO_ACCOUNT_SID=$(parse_yaml_value "$VARS_FILE" "twilio" "account_sid")
    TWILIO_AUTH_TOKEN=$(parse_yaml_value "$VARS_FILE" "twilio" "auth_token")
    TWILIO_FROM_NUMBER=$(parse_yaml_value "$VARS_FILE" "twilio" "from_number")

    if [ -n "$TWILIO_ACCOUNT_SID" ]; then
        echo "" >> "$ENV_FILE"
        echo "# Twilio Configuration" >> "$ENV_FILE"
        echo "TWILIO_ACCOUNT_SID=${TWILIO_ACCOUNT_SID}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} TWILIO_ACCOUNT_SID"
    fi

    if [ -n "$TWILIO_AUTH_TOKEN" ]; then
        echo "TWILIO_AUTH_TOKEN=${TWILIO_AUTH_TOKEN}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} TWILIO_AUTH_TOKEN"
    fi

    if [ -n "$TWILIO_FROM_NUMBER" ]; then
        echo "TWILIO_FROM_NUMBER=${TWILIO_FROM_NUMBER}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} TWILIO_FROM_NUMBER"
    else
        echo -e "  ${YELLOW}⚠${NC} TWILIO_FROM_NUMBER not set (SMS will be simulated)"
    fi

    TWILIO_TEST_TO_NUMBER=$(parse_yaml_value "$VARS_FILE" "twilio" "test_to_number")
    if [ -n "$TWILIO_TEST_TO_NUMBER" ]; then
        echo "TWILIO_TEST_TO_NUMBER=${TWILIO_TEST_TO_NUMBER}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} TWILIO_TEST_TO_NUMBER (all SMS will go to this number)"
    fi

    # Gmail Configuration
    GMAIL_USERNAME=$(parse_yaml_value "$VARS_FILE" "gmail" "username")
    GMAIL_APP_PASSWORD=$(parse_yaml_value "$VARS_FILE" "gmail" "app_password")
    GMAIL_ADJUSTER_EMAIL=$(parse_yaml_value "$VARS_FILE" "gmail" "adjuster_email")

    if [ -n "$GMAIL_USERNAME" ] && [ -n "$GMAIL_APP_PASSWORD" ]; then
        echo "" >> "$ENV_FILE"
        echo "# Gmail Configuration" >> "$ENV_FILE"
        echo "GMAIL_USERNAME=${GMAIL_USERNAME}" >> "$ENV_FILE"
        echo "GMAIL_APP_PASSWORD=${GMAIL_APP_PASSWORD}" >> "$ENV_FILE"
        echo -e "  ${GREEN}✓${NC} GMAIL_USERNAME"
        echo -e "  ${GREEN}✓${NC} GMAIL_APP_PASSWORD"

        if [ -n "$GMAIL_ADJUSTER_EMAIL" ]; then
            echo "GMAIL_ADJUSTER_EMAIL=${GMAIL_ADJUSTER_EMAIL}" >> "$ENV_FILE"
            echo -e "  ${GREEN}✓${NC} GMAIL_ADJUSTER_EMAIL"
        else
            echo -e "  ${YELLOW}⚠${NC} GMAIL_ADJUSTER_EMAIL not set (will use default)"
        fi
    else
        echo -e "  ${YELLOW}⚠${NC} Gmail not configured (emails will be simulated)"
    fi

    echo ""

    # Validate at least one LLM API key is set
    if [ -z "$OPENAI_API_KEY" ] && [ -z "$ANTHROPIC_API_KEY" ] && [ -z "$GOOGLE_API_KEY" ]; then
        echo -e "${RED}ERROR: No LLM API key configured${NC}"
        echo "Please set openai.api_key, anthropic.api_key, or google.api_key in vars.yaml"
        exit 1
    fi

else
    echo -e "${RED}ERROR: vars.yaml not found${NC}"
    echo "Please create vars.yaml from vars.yaml.template:"
    echo "  cp vars.yaml.template vars.yaml"
    exit 1
fi

# Build if requested
if [ "$1" == "--build" ] || [ "$1" == "-b" ]; then
    echo
    echo -e "${YELLOW}Building all modules...${NC}"
    mvn clean package -DskipTests
    echo -e "${GREEN}✓ Build complete${NC}"
fi

echo
echo -e "${YELLOW}Starting Docker containers...${NC}"
echo

# Run docker-compose with the environment variable
docker-compose up --build -d

echo
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║         CRASH Hive is starting via Docker!                 ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo
echo "Services will be available at:"
echo "  • Impact Analyst:   http://localhost:8081"
echo "  • Environment:      http://localhost:8082"
echo "  • Policy:           http://localhost:8083"
echo "  • Services:         http://localhost:8084"
echo "  • Communications:   http://localhost:8085"
echo "  • Orchestrator:     http://localhost:8080"
echo
echo "To check status:  docker-compose ps"
echo "To view logs:     docker-compose logs -f"
echo "To stop:          ./stop.sh"
echo
