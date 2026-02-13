#!/usr/bin/env bash
# Start multiple Honeycomb instances with per-cell port overrides.
# Each instance gets its own log file under ./logs/.
#
# Usage:
#   ./scripts/run-multi-cells.sh SampleModel=9090 InventoryCell=9091
#
# Options (env vars):
#   JAR=path/to/jar       Override the JAR location
#   JAVA_OPTS="-Xmx1g"   Extra JVM flags
#   PROFILES=prod         Spring profiles (default: none)
#   LOG_DIR=./logs        Log file directory

set -eu

# ── Auto-detect JAR ──────────────────────────────────────────────────
if [[ -z "${JAR:-}" ]]; then
  # Try common locations (newest first)
  for candidate in \
    target/honeycomb-*.jar \
    examples/honeycomb-example/target/honeycomb-example-*.jar \
    honeycomb-core/target/honeycomb-core-*.jar; do
    # shellcheck disable=SC2086
    found=$(ls -t $candidate 2>/dev/null | head -1)
    if [[ -n "$found" && -f "$found" ]]; then
      JAR="$found"
      break
    fi
  done
fi

if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
  echo "ERROR: No Honeycomb JAR found. Run 'mvn package' first, or set JAR=path/to/jar" >&2
  exit 1
fi
echo "Using JAR: $JAR"

# ── Setup ─────────────────────────────────────────────────────────────
LOG_DIR="${LOG_DIR:-./logs}"
mkdir -p "$LOG_DIR"

PIDS=()
CELLS=()

cleanup() {
  echo ""
  echo "Stopping ${#PIDS[@]} instance(s)..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null && echo "  Stopped PID $pid" || true
  done
  wait 2>/dev/null
  echo "All instances stopped."
}
trap cleanup EXIT INT TERM

# ── Launch instances ──────────────────────────────────────────────────
if [[ $# -eq 0 ]]; then
  echo "Usage: $0 CellName=Port [CellName=Port ...]"
  echo "Example: $0 SampleModel=9090 InventoryCell=9091"
  exit 1
fi

PROFILE_ARGS=""
if [[ -n "${PROFILES:-}" ]]; then
  PROFILE_ARGS="--spring.profiles.active=$PROFILES"
fi

for pair in "$@"; do
  IFS='=' read -r name port <<< "$pair"
  if [[ -z "$name" || -z "$port" ]]; then
    echo "WARN: Skipping invalid pair: $pair" >&2
    continue
  fi

  LOGFILE="$LOG_DIR/honeycomb-${name}-${port}.log"
  echo "Starting $name on port $port → log: $LOGFILE"

  java ${JAVA_OPTS:-} -jar "$JAR" \
    --cell.ports."${name}"="${port}" \
    $PROFILE_ARGS \
    > "$LOGFILE" 2>&1 &

  PIDS+=("$!")
  CELLS+=("$name=$port (PID $!)")
done

# ── Summary ───────────────────────────────────────────────────────────
echo ""
echo "━━━ Running instances ━━━"
for cell in "${CELLS[@]}"; do
  echo "  • $cell"
done
echo ""
echo "Logs in: $LOG_DIR/"
echo "Press Ctrl+C to stop all instances."
echo ""

# Wait for all children
wait