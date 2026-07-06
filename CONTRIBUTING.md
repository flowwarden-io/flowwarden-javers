# Contributing to FlowWarden Javers Integration

This file covers the **project-specific development setup** only. Organization-wide
policies — DCO sign-off, signed commits, commit conventions, review process, license —
live in the shared [`flowwarden-io` contributing guide](https://github.com/flowwarden-io/.github/blob/main/CONTRIBUTING.md) and apply here too.

This module provides the native [Javers](https://javers.org/) audit-stream integration
for [FlowWarden Stream Core](https://github.com/flowwarden-io/flowwarden-stream-core).

## Prerequisites

- Java 17+
- Docker (integration tests provision a MongoDB container via Testcontainers)

## Build & test

```bash
git clone https://github.com/flowwarden-io/flowwarden-javers.git
cd flowwarden-javers
./mvnw clean verify
```

- Unit tests run under Surefire (`*Test`).
- Integration tests run under Failsafe (`*IntegrationTest`) and require Docker.
- New behavior must ship with a test.

## Package layout

- `io.flowwarden.javers` — **public** API (`@JaversStream`, `@OnInitial`, `@OnUpdate`,
  `@OnTerminal`, `JaversChangeContext`). Stable surface.
- `io.flowwarden.javers.internal` — implementation. May change between versions.

Most dependencies (`flowwarden-stream-core`, the Javers starter, Spring Data MongoDB, SLF4J)
are declared `provided` so consumers bring their own versions. Any new dependency must be
justified via an issue first. Apache 2.0 license headers are required on every source file.

## Changelog

Any user-visible change must add an entry under `[Unreleased]` in
[`CHANGELOG.md`](CHANGELOG.md), in the appropriate category. Internal-only changes don't need one.
