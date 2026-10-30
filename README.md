# Hierarchical Tenant Configuration with Live Propagation

**MoveInSync Backend Case Study 2 – SDE Java Backend Hiring Assessment (2026)**

A Spring Boot–based configuration service for managing tenant-specific settings across a hierarchy of **GLOBAL → CLIENT → OFFICE → SHIFT**. The service supports typed configuration definitions, hierarchical overrides, versioned changes, staged office rollouts, rollback, snapshot persistence, and live propagation to connected clients.

> **Scope note:** This README documents the functionality implemented and exercised in this project, based on the submitted case study and the development/testing completed. It does not claim that every requirement in the case study has been fully implemented or proven.

## 1. Problem statement

The case study describes a multi-tenant platform where many services need frequent access to configuration values. Calling a central configuration server for every read would add network latency and load. The solution therefore maintains local client snapshots for reads while propagating configuration changes to connected clients.

This implementation provides:
- A central configuration service with hierarchical configuration resolution.
- A Java client library that maintains a local configuration snapshot.
- Server-Sent Events (SSE) for change notifications, supported by periodic version checks.
- Persistence and recovery of the last saved configuration snapshot.

## 2. Technology stack

- **Java:** 21 (project Maven compiler target: Java 17)
- **Spring Boot:** 4.1.2
- **Build:** Maven Wrapper
- **Database:** MySQL 8
- **Persistence:** Spring Data JPA / Hibernate
- **Live updates:** Server-Sent Events (SSE)
- **Security:** Spring Security HTTP Basic authentication
- **Client library:** Java `HttpClient`, Jackson, `AtomicReference`, and scheduled refresh
- **Snapshot storage:** JSON file on disk

## 3. Implemented functionality

### 3.1 Configuration definitions and types

Configuration definitions include a key, type, validation constraints, and a global default.

The implemented configuration types are:

- `STRING`
- `INTEGER`
- `DOUBLE`
- `BOOLEAN`
- `DURATION`
- `TIME_WINDOW`
- `ENUM`
- `LIST`

Validation is performed for the supported types, including numeric constraints, duration/time-window formats, enum allowed values, and JSON-array list values. Invalid values are rejected by the service rather than being accepted as configuration updates.

### 3.2 Hierarchical overrides and resolution

The service supports the hierarchy:

`GLOBAL → CLIENT → OFFICE → SHIFT`

The most specific applicable override takes precedence. If no more-specific override exists, resolution falls back through the hierarchy to the global default.

The explain functionality returns the resolved value together with the level/source from which that value was obtained.

### 3.3 Versioned changes and history

Configuration updates are versioned. The change history records the previous and new values along with the author, reason, timestamp, and version information.

The service maintains version information for configuration changes and exposes a version-check API.

### 3.4 Atomic change sets

The service supports applying a group of configuration updates as one transaction. The implementation was exercised with an invalid item in a change set: the request was rejected and the earlier valid item in that same set was not left partially applied.

### 3.5 Staged office rollout and promotion

The implementation supports creating a staged rollout for selected offices and subsequently promoting the rollout. The office selection and promotion state are persisted in the rollout data.

The staged rollout creation and promotion flow was exercised during development.

### 3.6 Rollback

A rollback operation can restore a value from a selected historical version. The restored value is recorded through the normal override/history update path, creating a new change rather than moving the version history backwards.

### 3.7 Live propagation and version checks

The service uses SSE to notify connected clients of configuration changes. It also provides a version endpoint so clients can check for changes since a known version.

The Java client library includes:
- Registration of configuration keys to be consumed by the client.
- Initial refresh when the client starts.
- Periodic refresh/version checks (configured for a five-second interval).
- SSE event listening.
- `Last-Event-ID` support when reconnecting.
- Jittered reconnect behavior.
- Local value access, including typed accessors.
- Atomic publication of a refreshed immutable snapshot using `AtomicReference`.

The SSE event filtering implemented for tenant users limits delivery to the user's tenant and global events; administrative users have broader event visibility.

### 3.8 Snapshot persistence and server/database failure handling

The service persists configuration state to a JSON snapshot file and restores definitions, overrides, rollout information, and the last processed change identifier from that snapshot.

The recovery flow was exercised by stopping MySQL and confirming that the application could continue using its restored/in-memory state, then recovering after MySQL became available again.

The Java client library also supports loading its saved snapshot from disk so local reads can continue without a successful server request.

### 3.9 Authentication and tenant isolation

Spring Security HTTP Basic authentication is configured with development users:

| Role | Username | Local development password |
|---|---|---|
| ADMIN | `admin` | `Admin@123` |
| USER | `tenant1` | `Tenant@123` |
| USER | `tenant2` | `Tenant@123` |

Access behavior exercised during development:
- Unauthenticated requests are rejected with **401**.
- Authenticated users can perform permitted read operations.
- User write requests are rejected with **403**.
- User-scoped resolve/history access derives the tenant identity from the authenticated username rather than trusting a tenant ID supplied by the caller.
- Cross-tenant access was tested.

**Security note:** These are development credentials only. Replace them with secure, externally managed credentials before any non-local deployment. Do not use these credentials in production.

## 4. Client library

The Java client library is located at:

`client/TenantConfigClient.java`

It uses Java's built-in HTTP client and Jackson to communicate with the configuration service. The client accepts the service URL, tenant/office/shift context, credentials, and a snapshot file path.

Its implemented responsibilities include:
- Registering the keys the application needs.
- Fetching initial configuration.
- Maintaining an in-memory snapshot for local access.
- Exposing local and typed getters.
- Refreshing periodically and listening for SSE updates.
- Reconnecting with jitter and an event ID.
- Saving and loading a local snapshot.

The client library compiled successfully as part of the project. A complete independent end-to-end client application run was not recorded as verified.

## 5. Load and propagation exercises

The project contains SSE load/reconnect test utilities under:

`src/main/java/com/example/tenant_config/loadtest/`

The following simulated exercises were run during development:
- 500-client reconnect-storm simulation.
- 200-client propagation simulation.

Recorded SSE delivery latency results:

| Simulation | Average latency | p50 latency | p99 latency |
|---|---:|---:|---:|
| 500 clients | 1181.25 ms | 1174 ms | 1241 ms |
| 200 clients | 417.80 ms | 419 ms | 436 ms |

These are the recorded results of the SSE simulations. They are **not** measurements of the Java client's in-memory getter latency, and they should not be interpreted as a microsecond local-read benchmark.

The machine specifications and a reproducible throughput benchmark were not recorded, so no hardware-specific performance claim is made here.

## 6. Verification status against the case study

The table below maps the case-study requirements to the implementation and the verification completed. “Implemented” means the feature was present and exercised during development; “Partially verified” means some related functionality was exercised, but the exact proof requested by the case study was not completed or recorded.

| Case-study requirement | Project status |
|---|---|
| Typed definitions, validation rules, and global defaults | **Implemented and exercised** |
| GLOBAL → CLIENT → OFFICE → SHIFT overrides; most-specific-wins resolution | **Implemented and exercised** |
| Explain which level supplied a resolved value | **Implemented and exercised** |
| Record author, reason, and version for changes | **Implemented and exercised** |
| Client-side in-memory snapshot and local getters | **Implemented in client library; full end-to-end run not verified** |
| Bounded staleness using push plus periodic version checks | **SSE and periodic version-check mechanisms implemented; a formal all-clients-within-5-seconds guarantee was not proven** |
| 500-client reconnect storm with jitter | **Simulation exercised** |
| Send only changes since a client's last version | **Version endpoint and SSE event replay support implemented; exhaustive reconnect/out-of-order proof not recorded** |
| Atomic change sets | **Implemented; invalid change-set rollback behavior exercised** |
| Staged rollout to selected offices and later promotion | **Implemented and exercised** |
| Roll back to a previous version | **Implemented and exercised** |
| Rollback response containing a before/after diff | **Not verified as a returned diff** |
| Continue serving last good state when the database is unavailable | **Snapshot recovery behavior exercised with MySQL unavailable** |
| Client can start from a snapshot saved on disk | **Implemented in client library; full independent client startup test not recorded** |
| Reject invalid values before publishing | **Implemented and exercised** |
| Consistency model documented and version 41 arriving after 43 handled | **Not fully demonstrated with a dedicated stale/out-of-order test** |
| Continuous-read concurrency test proving no mixed state during atomic update | **Not completed/verified** |
| Microsecond benchmark for local client reads | **Not completed** |
| Propagation measurement to 200 simulated clients | **Measured; results shown above** |
| Kill-server failure test | **Database-unavailable snapshot recovery was exercised; a separate configuration-server kill/restart test was not recorded** |

### Additional evaluation criteria

The case study also lists authentication, complexity analysis, failure handling, OOP design, trade-offs, monitoring, caching, and exception handling as additional evaluation criteria.

Implemented items relevant to these criteria include:
- Layered Spring application structure with controllers, services, repositories, entities, models, and a client library.
- HTTP Basic authentication, role-based access, and tenant-scoped user reads.
- Snapshot caching and recovery.
- Global exception handling.
- SSE reconnect and version-check mechanisms.

The following were not completed or not verified as full evaluation deliverables:
- A complete time/space complexity and scaling analysis backed by a local-read benchmark.
- A complete metrics/monitoring package with operational counters such as lag, error rate, rejections, retries, and queue depth.
- A dedicated automated concurrency test for atomic reads.
- A comprehensive failure-injection suite for duplicate/out-of-order messages and partial failures.
- A design note containing the requested architecture diagram, sequence diagram, and failure-mode table.
- A one-command environment startup including MySQL (for example, Docker Compose).

## 7. Consistency and design notes

The implementation combines persisted configuration state with in-memory snapshots. Connected clients receive updates through SSE and periodically check the service version to recover from missed notifications. The client publishes a refreshed immutable snapshot atomically, so a reader accesses one published snapshot rather than a map being updated entry by entry.

The client-side version ordering behavior for a deliberately delayed older version (for example, receiving version 41 after version 43) was not separately verified in the recorded tests. Therefore, this README does not claim that this specific case has been proven.

## 8. Project structure

```text
tenant-config/
├── client/
│   └── TenantConfigClient.java
├── src/
│   ├── main/
│   │   ├── java/com/example/tenant_config/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── entity/
│   │   │   ├── exception/
│   │   │   ├── loadtest/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── snapshots/                 # Local runtime snapshot data; excluded from Git
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
└── test-api.http
```

## 9. Run locally

### Prerequisites

- JDK 21 installed.
- MySQL 8 installed and running.
- A database named `tenant_config_db`.

Create the database in MySQL:

```sql
CREATE DATABASE tenant_config_db;
```

The application uses the following datasource configuration in `src/main/resources/application.properties`:

```properties
spring.application.name=tenant-config
spring.datasource.url=jdbc:mysql://localhost:3306/tenant_config_db
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.datasource.hikari.initialization-fail-timeout=-1
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false
```

Set the database password in the same Windows CMD session before starting the application. Do not commit a real password to the repository.

```cmd
set DB_PASSWORD=your_local_mysql_password
```

From the project root, compile and run:

```cmd
mvnw.cmd clean compile
mvnw.cmd spring-boot:run
```

The application runs on port **8080** with the default Spring Boot configuration.

## 10. API testing

The repository includes `test-api.http`, which can be used with a compatible REST Client extension in VS Code to send the sample HTTP requests.

The implemented API areas include:
- Configuration definition and override operations.
- Configuration resolution and explain mode.
- Change history and version checks.
- SSE event subscription.
- Staged rollout and promotion.
- Rollback.

Use the development credentials listed in the security section for local testing. User-level requests are tenant-scoped.

## 11. Repository

GitHub: https://github.com/ankitapaul74/tenant_config

---

**Submission note:** This README intentionally distinguishes implemented functionality from requirements that remain unverified or incomplete. It does not claim completion of the case study's unimplemented proof tests, benchmarks, diagrams, monitoring deliverables, or deployment packaging.
