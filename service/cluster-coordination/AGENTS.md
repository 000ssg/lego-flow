
# service / cluster-coordination — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `cluster-coordination` module provides distributed coordination primitives built on etcd and Raft consensus. It enables shared state management, leader election, distributed locking, and key-value storage across cluster nodes.

## Key Interfaces

### EtcdClient
Connection to an etcd cluster. Manages endpoint failover, leader detection, and automatic reconnection. Provides access to KV store, leases, locks, elections, sessions, transactions, and watches.

### EtcdKVStore
Key-value operations: get, put, delete, range scan. Tracks revisions for optimistic concurrency control.

### EtcdLease
TTL-based lease: grant, keep-alive, revoke. Leases are the foundation for distributed coordination — locks and elections are built on top of leases.

### EtcdLock
Distributed mutex using leased CAS with revkey ordering. Implements acquire → extend → release semantics.

### EtcdElection
Leader election via CAS on leased keys. Supports campaign, resign, leadership observation. Implements `AutoCloseable`.

### EtcdWatcher
Polling-based watch with revision ordering. Supports prefix watches. Handles reconnection on disconnect.

### EtcdDiscovery
`ClusterMembership` SPI via etcd registrations. Nodes register with leased keys; watches detect membership changes.

### RaftLeaderElection
Lightweight Raft leader election simulation. Provides term-based election with vote request/response semantics. Useful for demos, testing, and embedded scenarios without etcd.

### RaftLogEntry
Log entry record with term, index, and EntryType enum (NORMAL, NOOP, CONFIG_CHANGE).

## Design Decisions

- **etcd as primary backend** — Provides all standard coordination primitives backed by Raft consensus
- **Separate Raft simulation** — `RaftLeaderElection` for environments without etcd (demos, testing, embedded)
- **Connection lifecycle** — `EtcdClient` handles endpoint failover, leader detection, auto-reconnect
- **Lease-based liveness** — Leases and sessions are the building blocks; locks and elections built on top
- **Async API** — All operations return `CompletableFuture` for non-blocking execution
- **Testcontainers** — Integration tests use testcontainers for real etcd validation
- **SPI-level simulation** — In-memory store for testing without real etcd servers
- **Leader.toString()** — Parsed via `parseLeader()` for observe() consistency

## Thread Safety

- EtcdKVStore uses async HTTP client (non-blocking)
- EtcdLease keep-alive with atomic state
- EtcdLock via etcd atomic compare-and-create
- EtcdWatcher reconnects on disconnect
- RaftLeaderElection uses synchronized vote/election methods

## Package Structure

```
ssg.legoflow.service.cluster.coordination/
  EtcdClient           — Connection to etcd cluster
  EtcdConfig           — Connection configuration (endpoints, auth, TLS, timeouts)
  EtcdKVStore          — Key-value operations
  EtcdLease            — TTL-based lease
  EtcdLock             — Distributed mutex
  EtcdElection         — Leader election
  EtcdSession          — Lease-bound session
  EtcdTransaction      — Compare-and-swap transactions
  EtcdWatcher          — Key prefix watch
  EtcdDiscovery        — ClusterMembership via etcd
  raft/
    RaftLeaderElection — Lightweight Raft leader election
    RaftLogEntry       — Log entry model
```

## Testing

- **Framework**: JUnit 5 + AssertJ + Mockito + Testcontainers
- **168 tests passing**
- `EtcdClientIntegrationTest` — 11 tests (failover, connection lifecycle) with testcontainers
- Raft tests validate election semantics without external dependencies
- In-memory store used for unit tests (no real etcd required)

## Dependencies

- **blocks** — core DP/DF data processing framework
- **service** — service lifecycle, scoped contexts
- **cluster-core** — membership SPI, `ClusterNode`, events
