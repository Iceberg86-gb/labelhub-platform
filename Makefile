SHELL := /bin/bash

COMPOSE_FILE := infra/docker-compose.yml
SUREFIRE_GOAL := org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test
JAVA17_HOME := $(shell \
	if [ -n "$$JAVA_HOME" ] \
		&& [ -x "$$JAVA_HOME/bin/java" ] \
		&& "$$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17\.'; then \
		printf '%s' "$$JAVA_HOME"; \
	elif command -v /usr/libexec/java_home >/dev/null 2>&1; then \
		/usr/libexec/java_home -v 17 2>/dev/null || true; \
	elif command -v java >/dev/null 2>&1 \
		&& java -version 2>&1 | grep -q 'version "17\.'; then \
		java_path="$$(readlink -f "$$(command -v java)" 2>/dev/null || command -v java)"; \
		cd "$$(dirname "$$java_path")/.." && pwd; \
	fi)
JAVA17_ENV := JAVA_HOME="$(JAVA17_HOME)" PATH="$(JAVA17_HOME)/bin:$$PATH"

.PHONY: doctor dev-up dev-down test-db dev-api verify migrate-check require-java17

require-java17:
	@if [ -z "$(JAVA17_HOME)" ]; then \
		echo "JDK 17 was not found via JAVA_HOME, /usr/libexec/java_home, or PATH."; \
		echo "Install a JDK 17 distribution, then rerun this target."; \
		exit 1; \
	fi
	@if ! "$(JAVA17_HOME)/bin/java" -version 2>&1 | grep -q 'version "17\.'; then \
		echo "Resolved JAVA17_HOME=$(JAVA17_HOME), but that is not Java 17."; \
		"$(JAVA17_HOME)/bin/java" -version; \
		exit 1; \
	fi

doctor:
	@echo "LabelHub dev environment"
	@echo "JAVA_HOME(17)=$(JAVA17_HOME)"
	@if [ -z "$(JAVA17_HOME)" ]; then \
		echo "JDK 17 was not found via JAVA_HOME, /usr/libexec/java_home, or PATH."; \
		echo "Current PATH java:"; java -version; \
		echo "Current Maven runtime:"; mvn -version; \
		exit 1; \
	fi
	@if ! "$(JAVA17_HOME)/bin/java" -version 2>&1 | grep -q 'version "17\.'; then \
		echo "Resolved JAVA17_HOME=$(JAVA17_HOME), but that is not Java 17."; \
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

test-db: dev-up
	@echo "Ensuring dev-only MySQL test database labelhub_test exists"
	@root_password="$${MYSQL_ROOT_PASSWORD:-labelhub-root}"; \
	mysql_user="$${MYSQL_USER:-labelhub}"; \
	mysql_password="$${MYSQL_PASSWORD:-labelhub}"; \
	docker compose -f $(COMPOSE_FILE) exec -T mysql mysql -uroot -p"$$root_password" -e "CREATE DATABASE IF NOT EXISTS labelhub_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$$mysql_user'@'%' IDENTIFIED BY '$$mysql_password'; GRANT ALL PRIVILEGES ON labelhub_test.* TO '$$mysql_user'@'%'; FLUSH PRIVILEGES;"

dev-api: test-db
	@echo "Starting API with dev-only local credentials from infra/docker-compose.yml"
	@cd services/api && \
		SPRING_PROFILES_ACTIVE=local \
		LABELHUB_LLM_PROVIDER_MASTER_KEY=dev-only-llm-provider-master-key-32b \
		OBJECT_STORAGE_ACCESS_KEY=labelhub \
		OBJECT_STORAGE_SECRET_KEY=labelhub-secret \
		LABELHUB_PA_INITIAL_PASSWORD=dev-only-pa-password \
		mvn spring-boot:run

verify: test-db require-java17
	@$(JAVA17_ENV) mvn -o -pl services/api test-compile $(SUREFIRE_GOAL)

migrate-check: test-db require-java17
	@$(JAVA17_ENV) mvn -o -pl services/api -Dtest=ApplicationContextStartupTest test-compile $(SUREFIRE_GOAL)
