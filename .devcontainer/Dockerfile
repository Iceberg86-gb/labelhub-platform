FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

RUN rm -f /etc/apt/sources.list.d/yarn*.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client maven netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/labelhub-maven-cache

COPY pom.xml ./pom.xml
COPY services/api/pom.xml ./services/api/pom.xml
COPY services/agent/pom.xml ./services/agent/pom.xml
COPY packages/contracts/openapi/labelhub.yaml ./packages/contracts/openapi/labelhub.yaml

RUN chown -R vscode:vscode /tmp/labelhub-maven-cache \
    && su vscode -c 'cd /tmp/labelhub-maven-cache && mvn -q -pl services/api -DskipTests test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test && mvn -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.2.5 && mvn -q dependency:get -Dartifact=org.junit.platform:junit-platform-launcher:1.10.5' \
    && rm -rf /tmp/labelhub-maven-cache
