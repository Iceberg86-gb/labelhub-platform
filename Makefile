SHELL := /bin/bash

COMPOSE_FILE := infra/docker-compose.yml
JAVA17_HOME := $(shell /usr/libexec/java_home -v 17 2>/dev/null)
JAVA17_ENV := JAVA_HOME="$(JAVA17_HOME)" PATH="$(JAVA17_HOME)/bin:$$PATH"

.PHONY: doctor dev-up dev-down verify migrate-check require-java17

require-java17:
	@if [ -z "$(JAVA17_HOME)" ]; then \
		echo "JDK 17 was not found via /usr/libexec/java_home -v 17."; \
		echo "Install a JDK 17 distribution, then rerun this target."; \
		exit 1; \
	fi
	@if ! "$(JAVA17_HOME)/bin/java" -version 2>&1 | grep -q 'version "17\.'; then \
		echo "/usr/libexec/java_home -v 17 resolved to $(JAVA17_HOME), but that is not Java 17."; \
		"$(JAVA17_HOME)/bin/java" -version; \
		exit 1; \
	fi

doctor:
	@echo "LabelHub dev environment"
	@echo "JAVA_HOME(17)=$(JAVA17_HOME)"
	@if [ -z "$(JAVA17_HOME)" ]; then \
		echo "JDK 17 was not found via /usr/libexec/java_home -v 17."; \
		echo "Current PATH java:"; java -version; \
		echo "Current Maven runtime:"; mvn -version; \
		exit 1; \
	fi
	@if ! "$(JAVA17_HOME)/bin/java" -version 2>&1 | grep -q 'version "17\.'; then \
		echo "/usr/libexec/java_home -v 17 resolved to $(JAVA17_HOME), but that is not Java 17."; \
		"$(JAVA17_HOME)/bin/java" -version; \
		echo "Current Maven runtime:"; mvn -version; \
		exit 1; \
	fi
	@$(JAVA17_ENV) java -version
	@$(JAVA17_ENV) mvn -version
	@node -v
	@pnpm -v
	@docker version --format '{{.Server.Version}}' 2>/dev/null || true

dev-up:
	@scripts/dev-up.sh

dev-down:
	@docker compose -f $(COMPOSE_FILE) down

verify: dev-up require-java17
	@$(JAVA17_ENV) mvn -o -pl services/api test

migrate-check: dev-up require-java17
	@$(JAVA17_ENV) mvn -o -pl services/api -Dtest=ApplicationContextStartupTest test
