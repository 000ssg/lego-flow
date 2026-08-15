# Phase 1 — Cluster Core Abstractions

## Module
`network/cluster/core` — foundation for all cluster protocols

## Goal
Define SPIs and data types that every cluster protocol must implement. Zero protocol-specific logic — pure abstraction layer.

## Design Decisions

### ClusterNode — Immutable Node Identity
- Fields: `id` (UUID), `host` (InetAddress), `port` (int), `role` (enum: SERVER, CLIENT, BOTH), `metadata` (Map<String, String>), `status` (enum: ACTIVE, SUSPECT, FAILED, LEAVING)
- Immutability via record-style API; builder for construction
- `equals`/`hashCode` based on `id`
- Serialization: `toJson`/`fromJson` for transport neutrality

### ClusterEvent — Sealed Hierarchy
```java
sealed interface ClusterEvent permits NodeJoined, NodeLeft, NodeFailed, NodeRecovered, LeaderChanged {}
```
- Each subtype is a record with `sourceNode`, `timestamp`, event-specific fields
- `LeaderChanged` additionally carries `newLeader` node reference
- Used by `ClusterMembership` callbacks

### ClusterMembership — SPI
```java
public interface ClusterMembership extends AutoCloseable {
    ClusterStatus status();
    void addListener(ClusterEventListener listener);
    void removeListener(ClusterEventListener listener);
    void leave(); // graceful leave, broadcasts bye
    // async variants returning CompletableFuture
}
```

### ClusterManager — Default Implementation
- Composable: inject Discovery, StateStore, MessagingBus, HealthChecker
- Lifecycle: `start()` initializes all components in dependency order using structured concurrency
- On member failure: marks SUSPECT → rechecks → marks FAILED → notifies listeners
- Heartbeat loop: periodic self-announce with configurable interval

## Testing Plan

### Unit Tests (no I/O)
- `ClusterNodeTest`: construction, immutability, equality, serialization round-trip
- `ClusterStatusTest`: status computation from member list
- `ClusterConfigTest`: defaults, validation (rejects invalid intervals, null names)
- `ClusterEventTest`: all 5 subtypes construction, equals, toString

### Contract Tests
- `ClusterMembershipContractTest`: abstract base; subclasses for each protocol extend it
  - Verify listener registration/unregistration
  - Verify status() reflects current members
  - Verify leave() triggers NodeLeft event
  - Verify close() is idempotent

### Lifecycle Tests
- `ClusterLifecycleTest`: structured concurrency scope; verify all components start/stop together
- Verify failure during start rolls back started components

### Simulation Tests
- `ClusterManagerTest`: in-memory mock discovery + mock state store
  - Node join → NodeJoined event delivered
  - Node fail (heartbeat miss × threshold) → NodeFailed event
  - Node leave → NodeLeft event; no rejoin on same ID

## Demo Plan
`ClusterSimulationDemo` — 3 virtual nodes, in-memory transport
1. Node A starts → solo cluster
2. Node B joins → A and B exchange heartbeats
3. Node C joins → 3-node cluster
4. Node B crashes → A and C mark B as FAILED
5. Node B recovers → rejoins, state reconciled
6. All nodes leave → clean shutdown

## Files to Create
```
network/cluster/pom.xml                        — aggregator POM
network/cluster/core/pom.xml                   — module POM
network/cluster/core/doc/REQUIREMENTS.md       — requirements + design
src/main/java/.../cluster/
  ClusterNode.java
  ClusterStatus.java
  ClusterConfig.java
  ClusterRole.java (enum)
  ClusterStatus.java (enum) — ACTIVE, SUSPECT, FAILED, LEAVING
  ClusterMembership.java (interface)
  ClusterEventListener.java (functional interface)
  ClusterEvent.java (sealed)
  ClusterEventImpl/NodeJoined.java
  ClusterEventImpl/NodeLeft.java
  ClusterEventImpl/NodeFailed.java
  ClusterEventImpl/NodeRecovered.java
  ClusterEventImpl/LeaderChanged.java
  ClusterManager.java
  ClusterTransport.java (interface)
  ClusterHealthChecker.java (interface)
src/test/java/.../cluster/
  ClusterNodeTest.java
  ClusterStatusTest.java
  ClusterConfigTest.java
  ClusterMembershipContractTest.java
  ClusterLifecycleTest.java
  ClusterEventTest.java
  ClusterManagerTest.java
  InMemoryClusterTransport.java (test util)
demos/src/main/java/.../cluster/
  ClusterSimulationDemo.java
```
