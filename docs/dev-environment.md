# Development Environment

LabelHub uses Java 17 for backend development, Node 26 with pnpm 9 for the web app,
and the Docker Compose services in `infra/docker-compose.yml` for local MySQL,
Redis, and MinIO.

## Host Setup

Install a JDK 17 distribution and make sure macOS can find it:

```bash
/usr/libexec/java_home -v 17
```

The project Makefile does not trust `PATH` for Maven. Backend targets export:

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

## Dev Container

The `.devcontainer` setup provides:

- JDK 17
- Node 26
- pnpm 9.15.0
- Docker outside of Docker for the host Docker or Colima daemon
- MySQL client tools

If you use Colima with the dev container, start Colima on the host before opening
the container.
