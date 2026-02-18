#!/usr/bin/env bash
# Deploy one or more Honeycomb cell WARs to a running Tomcat server.
#
# Replaces run-multi-cells.sh: instead of spawning one JVM process per cell,
# each cell is packaged as a WAR and deployed to a single Tomcat instance.
# Tomcat serves every cell on its own context path:
#   SampleModel.war  →  http://localhost:8080/SampleModel
#   InventoryCell.war →  http://localhost:8080/InventoryCell
#
# Usage:
#   ./scripts/deploy-to-tomcat.sh [OPTIONS] CellName=path/to/cell.war [...]
#
# Examples:
#   # Deploy two cells built from their Maven modules:
#   ./scripts/deploy-to-tomcat.sh SampleModel InventoryCell
#
#   # Deploy with explicit WAR paths:
#   ./scripts/deploy-to-tomcat.sh SampleModel=target/myapp.war InventoryCell=../inv/target/inv.war
#
#   # Start Tomcat after deploying:
#   TOMCAT_START=true ./scripts/deploy-to-tomcat.sh SampleModel InventoryCell
#
# Environment variables:
#   CATALINA_HOME   Tomcat installation directory (default: auto-detect via JAVA_HOME or $PATH)
#   TOMCAT_START    Set to "true" to start Tomcat after deploying (default: false)
#   TOMCAT_STOP     Set to "true" to stop Tomcat before deploying (default: false)
#   BUILD_FIRST     Set to "true" to run "mvn package -P war -DskipTests" before deploying
#   MVN             Maven command (default: mvn)
#   WAR_PROFILE     Maven profile for WAR builds (default: war)

set -eu

# ── Resolve CATALINA_HOME ────────────────────────────────────────────────────
if [[ -z "${CATALINA_HOME:-}" ]]; then
  # Try $JAVA_HOME/../ (common layout), then PATH
  for candidate in \
    "/opt/tomcat" \
    "/usr/local/tomcat" \
    "/usr/share/tomcat10" \
    "/usr/share/tomcat9" \
    "${JAVA_HOME:-}/.."; do
    if [[ -f "$candidate/bin/catalina.sh" ]]; then
      CATALINA_HOME="$(cd "$candidate" && pwd)"
      break
    fi
  done
fi

if [[ -z "${CATALINA_HOME:-}" || ! -f "${CATALINA_HOME}/bin/catalina.sh" ]]; then
  echo "ERROR: Tomcat not found. Set CATALINA_HOME to your Tomcat installation." >&2
  echo "       e.g.  CATALINA_HOME=/opt/tomcat ./scripts/deploy-to-tomcat.sh ..." >&2
  exit 1
fi

WEBAPPS="${CATALINA_HOME}/webapps"
MVN="${MVN:-mvn}"
WAR_PROFILE="${WAR_PROFILE:-war}"

echo "Tomcat: ${CATALINA_HOME}"
echo "Webapps: ${WEBAPPS}"

if [[ $# -eq 0 ]]; then
  echo ""
  echo "Usage: $0 [CellName] [CellName=path/to/cell.war] ..."
  echo ""
  echo "Examples:"
  echo "  $0 SampleModel                         # auto-detect WAR for SampleModel"
  echo "  $0 SampleModel=target/sample.war       # explicit WAR path"
  echo "  $0 SampleModel InventoryCell           # deploy two cells"
  echo "  BUILD_FIRST=true $0 SampleModel        # build WAR then deploy"
  exit 1
fi

# ── Optionally stop Tomcat ───────────────────────────────────────────────────
if [[ "${TOMCAT_STOP:-false}" == "true" ]]; then
  echo "Stopping Tomcat..."
  "${CATALINA_HOME}/bin/catalina.sh" stop 2>/dev/null || true
  sleep 2
fi

# ── Process each cell argument ───────────────────────────────────────────────
DEPLOYED=()

for arg in "$@"; do
  if [[ "$arg" == *"="* ]]; then
    IFS='=' read -r cell_name war_path <<< "$arg"
  else
    cell_name="$arg"
    war_path=""
  fi

  echo ""
  echo "── Cell: ${cell_name} ─────────────────────────────────────────────────"

  # Build WAR if requested or if no explicit path given
  if [[ "${BUILD_FIRST:-false}" == "true" || -z "$war_path" ]]; then
    echo "Building WAR..."
    WAR=$(find . -name "*.war" \( -path "*/target/*" \) 2>/dev/null | \
          grep -i "${cell_name}" | head -1 || true)

    if [[ -z "$WAR" ]] || [[ "${BUILD_FIRST:-false}" == "true" ]]; then
      "${MVN}" package -P "${WAR_PROFILE}" -DskipTests -q
      WAR=$(find . -name "*.war" -path "*/target/*" 2>/dev/null | head -1 || true)
    fi

    if [[ -z "$WAR" ]]; then
      echo "WARN: Could not find a WAR file for '${cell_name}'. Skipping." >&2
      continue
    fi
    war_path="$WAR"
  fi

  if [[ ! -f "$war_path" ]]; then
    echo "ERROR: WAR not found: ${war_path}" >&2
    continue
  fi

  dest="${WEBAPPS}/${cell_name}.war"
  echo "Deploying: ${war_path} → ${dest}"
  cp "$war_path" "$dest"
  DEPLOYED+=("${cell_name}")
done

echo ""
if [[ ${#DEPLOYED[@]} -eq 0 ]]; then
  echo "No cells were deployed."
  exit 1
fi

echo "Deployed cells: ${DEPLOYED[*]}"
echo ""
echo "Each cell is accessible at:"
for cell in "${DEPLOYED[@]}"; do
  echo "  http://localhost:8080/${cell}"
done

# ── Optionally start Tomcat ──────────────────────────────────────────────────
if [[ "${TOMCAT_START:-false}" == "true" ]]; then
  echo ""
  echo "Starting Tomcat..."
  "${CATALINA_HOME}/bin/catalina.sh" start
  echo "Tomcat started. Monitor logs with: tail -f ${CATALINA_HOME}/logs/catalina.out"
fi
