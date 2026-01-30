#!/usr/bin/env bash
# Simple script to run multiple instances of the app with domain port overrides.
# Usage: ./scripts/run-multi-domains.sh SampleModel=9090 AnotherDomain=9091

set -eu

JAR=target/honeycomb-0.1.0.jar
if [[ ! -f "$JAR" ]]; then
  echo "Jar not found at $JAR; run 'mvn package' first" >&2
  exit 1
fi

PIDS=()
for pair in "$@"; do
  IFS='=' read -r name port <<< "$pair"
  if [[ -z "$name" || -z "$port" ]]; then
    echo "Skipping invalid pair: $pair" >&2
    continue
  fi
  echo "Starting domain $name on port $port"
  java -jar "$JAR" --domain.ports.${name}=${port} &
  PIDS+=("$!")
done

echo "Started instances: ${PIDS[*]}"

echo "To stop: kill ${PIDS[*]}"
