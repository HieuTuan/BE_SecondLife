#!/bin/sh
set -eu

# Render supplies PORT; existing local setups supply SERVER_PORT.
if [ -n "${PORT:-}" ]; then
    export SERVER_PORT="$PORT"
fi

: "${SERVER_PORT:?Set PORT on Render or SERVER_PORT locally}"
: "${SERVER_ADDRESS:?Set SERVER_ADDRESS to 0.0.0.0}"
: "${JAVA_TOOL_OPTIONS:?Set JAVA_TOOL_OPTIONS for the container memory limit}"

exec java -Dserver.address="$SERVER_ADDRESS" -jar /app/app.jar "$@"
