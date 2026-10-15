# Changelog

All notable changes to FlowWarden Javers Integration will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `@RestartPolicy` on a `@JaversStream` class is honoured (per-stream cadence and ceiling of the managed restart loop, stream-core rc.7). (#30)

### Changed
- `@JaversStream` beans are now declared through stream-core's public registration API — a `StreamDefinitionContributor` contributes one `StreamSpec` per bean — instead of constructing stream-core's internal `ChangeStreamDefinition` and registering it in the internal `StreamRegistry`. No class from `io.flowwarden.stream.internal` is referenced any more, so stream-core minors no longer break this module (the positional constructor broke at rc.3, rc.4 and rc.7). The stream-core annotations found on the class are mapped to their spec equivalents; the Javers handler names shown by the Console (`JAVERS_OnInitial`, …) now go through `StreamSpec.handlerNames`. Bootstrap timing is unchanged: contributors run after all singletons are created and before any stream starts. Requires stream-core `1.0.0-rc.7`. (#30)
- The auto-configured bean is `JaversStreamContributor`; `JaversStreamBeanPostProcessor` is gone (it was internal).

### Removed

### Fixed
- `@JaversStream` is now a Spring stereotype (meta-annotated with `@Component`), like `@ChangeStream` in stream-core. A handler class declared as the README shows — in a scanned package, no `@Component` — was never a bean, so it was never discovered and no stream was registered, without any log. (#34)
- README no longer claims `@Filter` and `@Pipeline` work on `@JaversStream` classes — they never did (the post-processor passed `null` for both); filtering is by entity type, internally. (#30)

### Deprecated

### Security

## [1.0.0-rc.2] — 2026-08-28

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

[Unreleased]: https://github.com/flowwarden-io/flowwarden-javers/compare/v1.0.0-rc.2...HEAD
[1.0.0-rc.2]: https://github.com/flowwarden-io/flowwarden-javers/releases/tag/v1.0.0-rc.2
[1.0.0-rc.1]: https://github.com/flowwarden-io/flowwarden-javers/releases/tag/v1.0.0-rc.1
