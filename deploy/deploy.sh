#!/bin/bash

# This script helps to run specific auth methods using docker compose profiles.
# Ensure you are running this from the project root.

PROFILES=""

for arg in "$@"
do
    case $arg in
        --email) PROFILES="$PROFILES --profile auth-email" ;;
        --sms)   PROFILES="$PROFILES --profile auth-sms" ;;
        --id)    PROFILES="$PROFILES --profile auth-id" ;;
        --oauth2) PROFILES="$PROFILES --profile auth-oauth2" ;;
        --admin)  PROFILES="$PROFILES --profile admin" ;;
        --common) PROFILES="$PROFILES --profile auth-common" ;;
        --core)   PROFILES="$PROFILES --profile core" ;;
        --web)    PROFILES="$PROFILES --profile web" ;;
        --monitoring) PROFILES="$PROFILES --profile monitoring" ;;
        --tools)      PROFILES="$PROFILES --profile tools" ;;
        --dev)        PROFILES="$PROFILES --profile dev" ;;
        --all)
            PROFILES="--profile core --profile auth-email --profile auth-sms --profile auth-id --profile auth-oauth2 --profile admin --profile auth-common --profile web"
            ;;
    esac
done

if [ -z "$PROFILES" ]; then
    # Default profiles
    PROFILES="--profile core --profile web --profile auth-common"
fi

if [ ! -f .env ]; then
    echo "⚠️ .env file not found. Please create it first."
    exit 1
fi

echo "🚀 Pulling latest images..."
if [[ "$PROFILES" == *"--profile dev"* ]]; then
    echo "🛠️ Development mode enabled: Setting SPRING_PROFILES_ACTIVE=local-dev"
    export SPRING_PROFILES_ACTIVE=local-dev
    export ADMIN_BOOTSTRAP_ENABLED=true
fi
docker compose $PROFILES pull

echo "♻️ Restarting services: $PROFILES"
docker compose $PROFILES up -d --remove-orphans
