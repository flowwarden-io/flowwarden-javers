<p align="center">
  <strong>FlowWarden Javers</strong><br/>
  Native Javers audit stream integration for FlowWarden — react to audit changes in real time.
</p>

<p align="center">
  <a href="https://github.com/flowwarden-io/flowwarden-javers/actions/workflows/ci.yml"><img src="https://github.com/flowwarden-io/flowwarden-javers/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/Java-17%2B-orange.svg" alt="Java 17+"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.x-brightgreen.svg" alt="Spring Boot 3.x"></a>
</p>

---

## What is FlowWarden Javers?

FlowWarden Javers is an integration module that connects [Javers](https://javers.org) audit snapshots to [FlowWarden Stream Core](https://github.com/flowwarden-io/flowwarden-stream-core). Instead of manually watching the `jv_snapshots` collection, you declare a `@JaversStream` handler and receive **deserialized domain objects** with full **Javers metadata** — commit author, changed properties, version history.

Built on top of MongoDB Change Streams, it captures Javers audit events in real time with all of FlowWarden's reliability features: checkpoint/resume, retry, dead letter queue, and deployment modes.

## Features

- **`@JaversStream` declarative handlers** — annotate a class, watch an entity's audit trail
- **Typed lifecycle handlers** — `@OnInitial` (creation), `@OnUpdate` (modification), `@OnTerminal` (deletion)
- **Deserialized domain objects** — receive your entity (e.g., `Product`) not raw `Document`
- **Javers metadata** — commit author, changed properties, version, commit date via `JaversChangeContext`
- **Full FlowWarden support** — `@Checkpoint`, `@RetryPolicy`, `@DeadLetterQueue`, `@Filter`, `@Pipeline` all work
- **Auto-detection** — resolves Javers snapshot collection name from Javers properties
- **Spring Boot auto-configuration** — just add the dependency

## Quick Start

### 1. Add the dependencies

```xml
<dependency>
    <groupId>io.flowwarden</groupId>
    <artifactId>flowwarden-stream-core</artifactId>
    <version>1.0.0-MVP-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.flowwarden</groupId>
    <artifactId>flowwarden-javers</artifactId>
    <version>1.0.0-MVP-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.javers</groupId>
    <artifactId>javers-spring-boot-starter-mongo</artifactId>
    <version>7.7.0</version>
</dependency>
```

### 2. Enable FlowWarden and audit your repository

```java
@SpringBootApplication
@EnableFlowWarden
public class MyApp { }

@JaversSpringDataAuditable
public interface ProductRepository extends MongoRepository<Product, String> { }
```

### 3. Create a Javers stream handler

```java
@JaversStream(entityType = Product.class)
@Checkpoint(saveEveryN = 1)
public class ProductAuditHandler {

    @OnInitial
    void onCreated(Product product, JaversChangeContext<Product> ctx) {
        log.info("Created '{}' by {}", product.getName(),
            ctx.getCommitMetadata().getAuthor());
    }

    @OnUpdate
    void onUpdated(Product product, JaversChangeContext<Product> ctx) {
        log.info("Updated '{}' — changed: {} (v{})",
            product.getName(), ctx.getChangedProperties(), ctx.getVersion());
    }

    @OnTerminal
    void onDeleted(JaversChangeContext<Product> ctx) {
        log.info("Deleted entity {} by {}",
            ctx.getEntityId(), ctx.getCommitMetadata().getAuthor());
    }
}
```

That's it. Every time a `Product` is saved or deleted through the audited repository, Javers creates a snapshot and FlowWarden delivers it to your handler with the deserialized entity and full audit metadata.

## Handler Signatures

Handler methods annotated with `@OnInitial`, `@OnUpdate`, or `@OnTerminal` support the following signatures:

| Signature | Description |
|-----------|-------------|
| `void handle(T entity, JaversChangeContext<T> ctx)` | Entity + full context |
| `void handle(JaversChangeContext<T> ctx)` | Context only (useful for `@OnTerminal`) |
| `void handle(T entity)` | Entity only |

## JaversChangeContext

The `JaversChangeContext<T>` provides access to both Javers and FlowWarden metadata:

| Method | Returns | Description |
|--------|---------|-------------|
| `getSnapshot()` | `CdoSnapshot` | Full Javers snapshot object |
| `getSnapshotType()` | `SnapshotType` | `INITIAL`, `UPDATE`, or `TERMINAL` |
| `getCommitMetadata()` | `CommitMetadata` | Author, date, commit ID |
| `getChangedProperties()` | `List<String>` | Properties modified in this change |
| `getVersion()` | `long` | Javers version number |
| `getEntityId()` | `String` | Entity ID from Javers global ID |
| `saveCheckpointNow()` | `void` | Force immediate checkpoint save |
| `sendToDlq(reason)` | `void` | Manually route to Dead Letter Queue |

## Configuration

### Snapshot collection resolution

The Javers snapshot collection name is resolved in order:

1. `@JaversStream(snapshotCollection = "custom_snapshots")` — explicit override
2. `javers.snapshotCollectionName` property — from Javers configuration
3. `jv_snapshots` — default

### FlowWarden annotations

All standard FlowWarden annotations work on `@JaversStream` classes:

| Annotation | Purpose |
|------------|---------|
| `@Checkpoint` | Resume token persistence |
| `@RetryPolicy` | Exponential backoff on failure |
| `@DeadLetterQueue` | Route failed events to DLQ |
| `@Filter` | Application-side event filtering |
| `@Pipeline` | Additional server-side MongoDB filtering |

## Compatibility

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Java | 17 | 21 |
| Spring Boot | 3.2.x | 3.2.x+ |
| FlowWarden Stream Core | 1.0.0 | 1.0.0+ |
| Javers | 7.x | 7.7.0+ |
| MongoDB Server | 6.0 | 7.0+ |

## FlowWarden Ecosystem

| Component | Description | License |
|-----------|-------------|---------|
| **[flowwarden-stream-core](https://github.com/flowwarden-io/flowwarden-stream-core)** | Declarative MongoDB Change Streams library for Spring Boot | Apache 2.0 |
| **[flowwarden-javers](https://github.com/flowwarden-io/flowwarden-javers)** | Native Javers audit stream integration | Apache 2.0 |
| **flowwarden-reporter** | Connects your streams to FlowWarden Console for monitoring | Apache 2.0 |
| **FlowWarden Console** | Dashboard for monitoring, alerting, and managing Change Streams | Commercial |

## Documentation

Full documentation is available at **[docs.flowwarden.io](https://docs.flowwarden.io)**.

## License

FlowWarden Javers is licensed under the [Apache License, Version 2.0](LICENSE).
