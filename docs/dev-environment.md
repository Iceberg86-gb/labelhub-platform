# Development Environment

LabelHub uses Java 17 for backend development, Node 26 with pnpm 9 for the web app,
and the Docker Compose services in `infra/docker-compose.yml` for local MySQL,
Redis, and MinIO.

## Host Setup

Install a JDK 17 distribution and make sure macOS can find it:

```bash
/usr/libexec/java_home -v 17
```

The verification Makefile targets do not trust `PATH` for Maven. They export:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Run:

```bash
make doctor
```

to print the effective Java, Maven, Node, pnpm, and Docker versions.

## Local Services

Start Docker first. If you use Colima:

```bash
colima start
```

Then run:

```bash
make dev-up
```

This runs `docker compose -f infra/docker-compose.yml up -d` and waits until the
MySQL service is healthy before returning.

To stop local services:

```bash
make dev-down
```

## Test Database

Backend tests use a separate MySQL schema named `labelhub_test` in the same dev
MySQL container. The test datasource is configured in
`services/api/src/test/resources/application.yml`:

```text
jdbc:mysql://localhost:3306/labelhub_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
```

Create or refresh the schema and grants before running tests:

```bash
make test-db
```

The target is idempotent. It starts local services, creates `labelhub_test` if it
does not exist, and grants the dev `labelhub` user access. Flyway creates the
test tables on startup. The 93 integration tests that rely on Testcontainers are
still skipped when Docker/Testcontainers is unavailable; this batch keeps that
existing boundary unchanged.

## Running The API Locally

Use the Makefile target for a bare API process on the host:

```bash
make dev-api
```

This starts `services/api` with `SPRING_PROFILES_ACTIVE=local` and dev-only
credentials that match `infra/docker-compose.yml`:

```text
LABELHUB_LLM_PROVIDER_MASTER_KEY=dev-only-llm-provider-master-key-32b
OBJECT_STORAGE_ACCESS_KEY=labelhub
OBJECT_STORAGE_SECRET_KEY=labelhub-secret
LABELHUB_PA_INITIAL_PASSWORD=dev-only-pa-password
```

The MinIO dev credentials are `labelhub` / `labelhub-secret`, and the bucket is
`labelhub-exports`. These values are for local development only; production
secrets must stay out of the repository.

Equivalent bare command:

```bash
cd services/api
SPRING_PROFILES_ACTIVE=local \
LABELHUB_LLM_PROVIDER_MASTER_KEY=dev-only-llm-provider-master-key-32b \
OBJECT_STORAGE_ACCESS_KEY=labelhub \
OBJECT_STORAGE_SECRET_KEY=labelhub-secret \
LABELHUB_PA_INITIAL_PASSWORD=dev-only-pa-password \
mvn spring-boot:run
```

If these variables are missing, the app fails loudly during startup or during the
first storage/provider operation: provider secret encryption needs
`LABELHUB_LLM_PROVIDER_MASTER_KEY`, platform-admin bootstrap needs
`LABELHUB_PA_INITIAL_PASSWORD`, and MinIO access needs the object-storage access
and secret keys.

## Verification

Use the unified backend verification entrypoint:

```bash
make verify
```

It starts local services with `make dev-up`, then runs:

```bash
mvn -o -pl services/api test
```

with `JAVA_HOME` pinned to JDK 17.

## Deploying Web Assets

Use the deployment helper for the single-host production web sync:

```bash
scripts/deploy-web.sh
```

It runs `pnpm --filter @labelhub/web build`, syncs `apps/web/dist/` to
`root@120.26.182.61:/opt/labelhub/infra/web-dist/` with `--delete`, then syncs
the source tree to `/opt/labelhub/` without `--delete`. SSH uses
`~/.ssh/labelhub-deploy.pem`. After publish, the public entry is
`http://120.26.182.61:8443/`; the local Vite address `http://127.0.0.1:5173`
is not usable by external users.

Preview the rsync plan without changing the server:

```bash
scripts/deploy-web.sh --dry-run
```

Dry-run still performs the local web build, then adds `-n --itemize-changes` to
both rsync calls. The source sync keeps `.env*.example` available and excludes
`node_modules`, `.git`, `dist`, `.env`, `.env.*`, `.env.prod`, `web-dist`,
`.DS_Store`, `.pnpm-store`, `.claude`, `.codex`, `application-secrets.yml`,
`logs`, `*.log`, `coverage`, `*.tsbuildinfo`, `mysql-data`, `minio-data`,
`target`, root `*.bundle`, `submission`, `docs/screenshots`, and
`docs/design-assets`.

## Dev Container

The `.devcontainer` setup provides:

- JDK 17
- Node 26
- pnpm 9.15.0
- Docker outside of Docker for the host Docker or Colima daemon
- MySQL client tools

If you use Colima with the dev container, start Colima on the host before opening
the container.
