#!/usr/bin/env bash
# verify-backend.sh — run the services/api backend checks on a host without a local JDK 17.
#
# The backend targets Java 17 / Spring Boot 3.2. On a machine that only has another JDK, this
# runs Maven inside the maven:3.9-eclipse-temurin-17 image (the same image
# services/api/Dockerfile uses for production builds), so compile/test/migration-check stay
# reproducible without installing or switching the host JDK. It is a thin, additive wrapper —
# it does not change any application behaviour.
#
# The Testcontainers integration tests talk to the host Docker daemon (docker-outside-of-docker)
# to spin up an ephemeral MySQL per test class. ApplicationContextStartupTest instead needs a
# reachable MySQL at the datasource URL, so it runs against the dev MySQL from
# infra/docker-compose.yml (start it first with `make dev-up`).
#
# Usage:
#   scripts/verify-backend.sh                # compile + full test suite + migrate-check
#   scripts/verify-backend.sh compile        # test-compile only (no Docker daemon needed)
#   scripts/verify-backend.sh test           # unit + Testcontainers integration tests
#   scripts/verify-backend.sh migrate-check  # Flyway validate + Spring context startup vs dev MySQL
#
# Env overrides:
#   LABELHUB_M2_VOLUME   Docker volume name for the Maven cache (default: labelhub-m2)
#   LABELHUB_MAVEN_IMAGE JDK 17 Maven image (default: maven:3.9-eclipse-temurin-17)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${LABELHUB_MAVEN_IMAGE:-maven:3.9-eclipse-temurin-17}"
M2_VOLUME="${LABELHUB_M2_VOLUME:-labelhub-m2}"
MYSQL_CONTAINER="${LABELHUB_DEV_MYSQL_CONTAINER:-infra-mysql-1}"
TEST_DB_URL='jdbc:mysql://'"${MYSQL_CONTAINER}"':3306/labelhub_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
GOAL="${1:-all}"

if ! docker info >/dev/null 2>&1; then
  echo "docker is not reachable; start Docker (and 'make dev-up' for migrate-check) first." >&2
  exit 1
fi

docker volume create "$M2_VOLUME" >/dev/null

# Newer Docker Engine (>=25, API >=1.44) rejects the docker-java client's default API
# negotiation (1.32). Forcing api.version via a JVM system property fixes it inside the forked
# surefire JVM; the DOCKER_API_VERSION env var is not honoured by this docker-java build.
testcontainers_args() {
  printf '%s\0' \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --add-host=host.docker.internal:host-gateway \
    -e DOCKER_HOST=unix:///var/run/docker.sock \
    -e JAVA_TOOL_OPTIONS=-Dapi.version=1.44 \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    -e TESTCONTAINERS_RYUK_DISABLED=true
}

dev_mysql_network() {
  docker inspect "$MYSQL_CONTAINER" \
    --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || true
}

run_mvn() {
  # shellcheck disable=SC2046
  docker run --rm \
    -v "$ROOT_DIR":/workspace -w /workspace \
    -v "$M2_VOLUME":/root/.m2 \
    "$@"
}

do_compile() {
  echo ">> test-compile (JDK 17 container)"
  run_mvn "$IMAGE" mvn -pl services/api -DskipTests test-compile
}

do_test() {
  echo ">> unit + Testcontainers integration tests (excludes ApplicationContextStartupTest)"
  local tc_args=()
  while IFS= read -r -d '' arg; do tc_args+=("$arg"); done < <(testcontainers_args)
  run_mvn "${tc_args[@]}" "$IMAGE" \
    mvn -pl services/api '-Dtest=!ApplicationContextStartupTest' test
}

do_migrate_check() {
  echo ">> migrate-check: Flyway validate + Spring context startup vs dev MySQL"
  local net
  net="$(dev_mysql_network)"
  run_mvn --network "${net:-infra_default}" \
    -e SPRING_DATASOURCE_URL="$TEST_DB_URL" \
    "$IMAGE" mvn -pl services/api -Dtest=ApplicationContextStartupTest test
}

case "$GOAL" in
  compile)       do_compile ;;
  test)          do_test ;;
  migrate-check) do_migrate_check ;;
  all)           do_compile; do_test; do_migrate_check ;;
  *) echo "usage: $0 [all|compile|test|migrate-check]" >&2; exit 2 ;;
esac

echo "verify-backend: ${GOAL} OK"
