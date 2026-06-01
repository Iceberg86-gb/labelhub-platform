FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

RUN rm -f /etc/apt/sources.list.d/yarn*.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*
