# Changelog

All notable changes to FlowWarden Javers Integration will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed
- `flowwarden-stream-core` baseline: 1.0.0-rc.4. No code change needed — Javers streams register a typed `INSERT` handler only (`jv_snapshots` writes are always inserts at the change stream level), so none of core's dispatch changes affect this layer. Inherited runtime improvements worth knowing: a dropped/renamed `jv_snapshots` collection is now detected and handled per the stream's `onHistoryLost` strategy (self-heal or fail-stop) instead of leaving a silently dead cursor, runtime cursor death auto-restarts through the resume cascade, and idle Javers streams gain the oplog-rollover heartbeat protection (on by default).

### Removed

### Fixed

### Deprecated

### Security

## [1.0.0-rc.1] — 2026-07-06

### Added
- Initial public release of the FlowWarden Javers integration — connects [Javers](https://javers.org) audit snapshots to [FlowWarden Stream Core](https://github.com/flowwarden-io/flowwarden-stream-core) so applications react to audit changes in real time instead of manually watching the `jv_snapshots` collection.
- `@JaversStream` — declarative, class-level handler that subscribes to an audited entity's trail and receives the **deserialized domain object** plus Javers metadata (commit author, changed properties, version history) via `JaversChangeContext`.
- `@OnInitial`, `@OnUpdate`, `@OnTerminal` — method annotations to route the initial snapshot, updates, and terminal (delete) events of an entity to dedicated handlers.
- Spring Boot auto-configuration wiring the Javers stream on top of stream-core's MongoDB change-stream engine, inheriting its reliability features (checkpoint/resume, retry, dead-letter queue, deployment modes).
- Flexible handler signatures — a handler may accept the domain object, the `JaversChangeContext`, or both, resolved at registration time.

### Changed

### Removed

### Fixed

### Deprecated

### Security

[Unreleased]: https://github.com/flowwarden-io/flowwarden-javers/compare/v1.0.0-rc.1...HEAD
[1.0.0-rc.1]: https://github.com/flowwarden-io/flowwarden-javers/releases/tag/v1.0.0-rc.1
