
# network / cluster / core — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `cluster-core` module provides foundational abstractions for multi-node clustering. It defines the cluster membership SPI, event model, health checking, and consistent hashing used by all higher-level cluster protocols.

## Key Interfaces

### ClusterMembership
Primary extension point. Implementations provide node discovery, membership tracking, and event broadcasting. Concrete implementations live in separate modules (`cluster-discovery`, `cluster-coordination`).

### ClusterNode
Immutable record: `id`, `host`, `port`, `role`, `status`, `metadata`. Status transitions: `ACTIVE → SUSPECT → FAILED` (failure detection), `FAILED → ACTIVE` (recovery).

### ClusterEvent
Sealed interface hierarchy: `NodeJoined`, `NodeLeft`, `NodeFailed`, `NodeRecovered`, `LeaderChanged`. All extend `ClusterEvent` with node + timestamp.

### ClusterManager
Default implementation combining heartbeat scheduling, health checking, failure detection, and event broadcasting. Uses `ScheduledExecutorService` for periodic tasks.

### ClusterTransport
SPI for inter-node messaging. Decouples clustering logic from networking — any transport (TCP, UDP, NATS, etc.) can be plugged in.

### ClusterHealthChecker
SPI for probing remote node health. Default implementation uses simple TCP probes.

### ConsistentHashRing
Ketama-style consistent hashing with configurable virtual node replicas. Each physical node maps to N virtual nodes on a 32-bit hash ring. Key lookups find the next virtual node clockwise.

## Design Decisions

- **Transport abstraction** — `ClusterTransport` decouples clustering logic from networking
- **Health checking** — `ClusterHealthChecker` is pluggable; default uses TCP probes
- **Thread safety** — All state uses concurrent collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicReference`)
- **Leader tracking** — Manager tracks a leader node via `AtomicReference`; leader is set via `setLeader()` by coordination protocols (e.g., etcd, Raft)
- **No embedded transport** — This module does not implement a transport; consumers provide one via SPI

## Thread Safety Model

All mutable state uses concurrent primitives:
- Members: `ConcurrentHashMap<String, ClusterNode>`
- Heartbeat timestamps: `ConcurrentHashMap<String, Instant>`
- Event listeners: `CopyOnWriteArrayList<ClusterEventListener>`
- Leader: `AtomicReference<ClusterNode>`

## Package Structure

```
ssg.legoflow.network.cluster.core/
  ClusterNode              — Immutable node descriptor (record)
  ClusterNodeStatus        — Lifecycle status enum
  ClusterRole              — Node role enum
  ClusterConfig            — Runtime configuration (record + builder)
  ClusterStatus            — Snapshot of cluster state
  ClusterMembership        — SPI for membership management
  ClusterManager           — Default implementation with heartbeat, health check
  ClusterTransport         — SPI for inter-node messaging
  ClusterHealthChecker     — SPI for probing remote node health
  ClusterEvent             — Sealed event hierarchy
  ClusterEventListener     — Functional consumer for events
  hashing/
    ConsistentHashRing     — Ketama-style consistent hash ring
    ConsistentHasher       — SPI for consistent hashing
    HashFunction           — SPI for hash computation
    MurmurHash3            — MurmurHash3 128-bit implementation
    KetamaNodeAddress      — Virtual node address with hash precomputation
```

## Testing

- **Framework**: JUnit 5 + AssertJ + Mockito
- **125 tests passing**
- Contract test (`ClusterMembershipContractTest`) validates SPI behavior
- Tests use `InMemoryClusterTransport` for transport-agnostic verification

## Dependencies

- **blocks** — core DP/DF data processing framework
- **service** — service lifecycle, scoped contexts
- **network-common** — shared utilities
