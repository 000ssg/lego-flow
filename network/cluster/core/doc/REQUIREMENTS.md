# Cluster Core — Requirements & Design

## Commit: fa90d15 — Cluster Core Abstractions (2026-08-15)

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality (sharing state, workload balancing, discovery, optimized processing). Cover generic networking as well as HTTP-related activities (supporting web servers cluster). Create plan with reasonable split into phases."

### Reformulated Requirements
1. Define `ClusterNode` — immutable node descriptor (id, host, port, role, metadata, status)
2. Define `ClusterEvent` sealed hierarchy: NodeJoined, NodeLeft, NodeFailed, NodeRecovered, LeaderChanged
3. Define `ClusterMembership` SPI for membership change callbacks
4. Define `ClusterManager` — composable coordinator (discovery + state + messaging)
5. Define `ClusterConfig` — configuration record with validation
6. Define `ClusterTransport` SPI — transport-agnostic node communication
7. Define `ClusterHealthChecker` SPI — periodic health probe
8. Define `ConsistentHashRing` — Ketama-style virtual-node ring for workload distribution
9. All types must support dual API (procedural + functional default methods)
10. Structured concurrency for lifecycle management

### Final Design Decisions
- **Package:** `ssg.legoflow.network.cluster.core`
- **Module:** `network/cluster/core` (under `network/cluster` aggregator)
- **Dependencies:** blocks, service, network-common
- **No external dependencies** — pure abstraction layer
- **ConsistentHashing** included in core (not separate module) — it's a data structure, not a protocol
- **Enum types** for role and status to enable pattern matching on sealed interfaces

### Implementation Details
- Core data types: `ClusterNode`, `ClusterStatus`, `ClusterConfig`, `ClusterRole`, `ClusterStatus` (enum)
- SPIs: `ClusterMembership`, `ClusterEventListener`, `ClusterTransport`, `ClusterHealthChecker`
- Event types: 5 sealed subtypes of `ClusterEvent`
- Manager: `ClusterManager` with composable discovery + state + messaging
- Hashing: `ConsistentHashRing`, `HashFunction`, `MurmurHash3`, `ConsistentHasher`, `KetamaNodeAddress`
- Tests: unit tests for all types, contract tests for SPIs, simulation tests for manager
- Demo: `ClusterSimulationDemo` — 3-node in-memory cluster

### Test Coverage
- `ClusterNodeTest` — construction, immutability, equality, serialization
- `ClusterStatusTest` — status computation
- `ClusterConfigTest` — defaults, validation
- `ClusterEventTest` — all 5 subtypes
- `ClusterMembershipContractTest` — abstract contract; subclasses extend
- `ClusterLifecycleTest` — structured concurrency
- `ClusterManagerTest` — in-memory simulation
- `ConsistentHashRingTest` — ring operations, add/remove, lookup
- `MurmurHash3Test` — test vectors
- `KetamaRedistributionTest` — redistribution bound verification
- `ConsistentHasherTest` — key→node mapping

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~15000 |
| Agent tool calls | ~25 |
| Agent wall time | ~30 min |
| Files created/modified | ~25 |
| Lines added/removed | +1200 / -0 |
| Tests added | ~12 (total: ~12) |
