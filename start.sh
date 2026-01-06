#!/bin/bash

# CRASH - Claims Response Agent System Hive
# Startup Script (Docker Compose)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VARS_FILE="${SCRIPT_DIR}/vars.yaml"

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

# Parse YAML value
parse_yaml() {
    local file=$1
    local key=$2
    grep -E "^\s*${key}:" "$file" 2>/dev/null | head -1 | sed 's/[^:]*:[[:space:]]*//' | sed 's/^"//' | sed 's/"$//' | sed "s/^'//" | sed "s/'$//"
}

# Load API key from vars.yaml
if [ -f "$VARS_FILE" ]; then
    echo -e "${GREEN}✓ Loading configuration from vars.yaml${NC}"
    OPENAI_API_KEY=$(parse_yaml "$VARS_FILE" "api_key" | head -1)

    if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "sk-your-openai-api-key-here" ]; then
        echo -e "${RED}ERROR: Please set your OpenAI API key in vars.yaml${NC}"
        exit 1
    fi

    export OPENAI_API_KEY
    # Write to .env for docker-compose to pick up
    echo "OPENAI_API_KEY=${OPENAI_API_KEY}" > "${SCRIPT_DIR}/.env"
    echo -e "${GREEN}✓ OPENAI_API_KEY loaded${NC}"
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
