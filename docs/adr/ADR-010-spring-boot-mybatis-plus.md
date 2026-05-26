# ADR-010 Spring Boot MyBatis-Plus Java 17

## Status

Accepted

## Decision

The backend uses Java 17, Spring Boot 3, and MyBatis-Plus.

## Consequences

- Java is aligned with the project implementation direction and course-allowed backend options.
- MyBatis-Plus works well with explicit SQL, JSON columns, and append-only tables.
- The project avoids adding a separate ORM abstraction layer before the domain is stable.
