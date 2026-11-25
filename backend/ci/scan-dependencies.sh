#!/usr/bin/env bash
# Scans the runtime dependencies for known vulnerabilities (Phase 9, the plan's "Trivy" pipeline gate).
#
# It lists the runtime dependencies with Maven, turns the list into a small CycloneDX SBOM and hands that to Trivy. This
# is lighter and more exact than scanning the packaged jar: every dependency is named once with its resolved version.
# Fails (exit 1) when a HIGH or CRITICAL vulnerability that has a fix is found.
#
# Needs: Docker and network (Maven Central, and Trivy's vulnerability database on first use).
# Run from anywhere:  backend/ci/scan-dependencies.sh      Other severities:  SEVERITY=CRITICAL backend/ci/scan-dependencies.sh
set -euo pipefail

TRIVY_IMAGE="${TRIVY_IMAGE:-ghcr.io/aquasecurity/trivy:0.76.2}"
SEVERITY="${SEVERITY:-HIGH,CRITICAL}"
# The cache volume keeps Trivy's vulnerability database between runs. TRIVY_SKIP_DB_UPDATE and TRIVY_DB_REPOSITORY (a
# mirror) are passed through to Trivy, for networks that cannot reach its default database registry.
TRIVY_CACHE_VOLUME="${TRIVY_CACHE_VOLUME:-trivy-cache}"

cd "$(dirname "$0")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

./mvnw -B -q dependency:list -DincludeScope=runtime -DoutputFile="$work/deps.txt"

# "   group:artifact:jar:version:scope" (or with a classifier) -> group:artifact:version, one per line
{ grep -E '^[[:space:]]+[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:' "$work/deps.txt" || true; } | sed 's/ -- .*//; s/^ *//' \
  | awk -F: '{ if (NF == 5) { print $1 ":" $2 ":" $4 } else if (NF >= 6) { print $1 ":" $2 ":" $5 } }' \
  | sort -u > "$work/gav.txt"

count="$(wc -l < "$work/gav.txt" | tr -d ' ')"
if [ "$count" -lt 50 ]; then
  echo "Only $count dependencies were found: the dependency list could not be read, so nothing was scanned." >&2
  exit 2
fi

{
  echo '{"bomFormat":"CycloneDX","specVersion":"1.5","version":1,"components":['
  awk -F: 'BEGIN { first = 1 } {
    if (!first) printf ","; first = 0
    printf "{\"type\":\"library\",\"name\":\"%s\",\"group\":\"%s\",\"version\":\"%s\",\"purl\":\"pkg:maven/%s/%s@%s\"}\n", $2, $1, $3, $1, $2, $3
  }' "$work/gav.txt"
  echo ']}'
} > "$work/sbom.json"

echo "Scanning $count runtime dependencies for $SEVERITY vulnerabilities with $TRIVY_IMAGE ..."

# a Windows shell needs the mount path in Windows form and must not rewrite the paths inside the container
mount="$work"
if command -v cygpath > /dev/null 2>&1; then
  mount="$(cygpath -m "$work")"
fi
MSYS_NO_PATHCONV=1 docker run --rm -v "$mount:/scan:ro" -v "$TRIVY_CACHE_VOLUME:/root/.cache" \
  -e TRIVY_SKIP_DB_UPDATE -e TRIVY_DB_REPOSITORY "$TRIVY_IMAGE" \
  sbom --severity "$SEVERITY" --ignore-unfixed --exit-code 1 --no-progress /scan/sbom.json
