#!/usr/bin/env bash
# Run AI WeatherMan with a Java debugger port and DEBUG logging.
#   ./run-debug.sh            start without waiting for a debugger
#   SUSPEND=y ./run-debug.sh  wait until a debugger attaches (to break on startup code)
# Settings and secrets come from .env (git-ignored). The debugger and the app both bind to
# 127.0.0.1 only, so neither is reachable from other machines.
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "Missing .env (needs AWS_BEARER_TOKEN_BEDROCK and the WEATHERMAN_* settings)." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

JAR=target/weatherman-0.0.1-SNAPSHOT.jar
if [[ ! -f "$JAR" ]]; then
  echo "Building $JAR ..."
  mvn -q -B package
fi

exec java \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend="${SUSPEND:-n}",address=127.0.0.1:5005 \
  -jar "$JAR" \
  --logging.level.com.example.weatherman=DEBUG
