FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
