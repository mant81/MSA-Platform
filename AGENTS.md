# MSA PLATFORM Guidelines

## Core Rules
- Use `com.msa` as the base package.
- Do not use `find*` naming for read operations.
- Use `select*` naming for query and read operations.
- Keep code simple: `controller`, `service`, `mapper`, `xml`, `vo`.
- Do not add DTO layers unless explicitly requested.
- Prefer per-service DB configuration with H2 as the default.

## Service Rules
- Each service is an independent Spring Boot application.
- `msa-core` is shared as a common jar dependency.
- `gateway-service` is the entry point and routes traffic.
- Database type selection is handled by service configuration.

## Project Work Principles
- Follow `PROJECT_WORK_PRINCIPLES.md` for the combined my_harness and MSA development rules.
- Keep changes minimal, service-specific, and trace-aware.
- Prefer gateway-first test flows, H2-backed service samples, and explicit service boundaries over shared abstractions.
