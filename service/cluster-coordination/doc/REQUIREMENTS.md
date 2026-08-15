# Cluster Coordination — etcd/Raft — Requirements & Design

## Commit: (planned) — etcd/Raft Shared State

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality"

### Reformulated Requirements
1. etcd v3 gRPC client: KV operations (Range, Put, DeleteRange, Txn)
2. Watch API: server-streaming watch with revision ordering and gap detection
3. Lease management: grant, keep-alive, revoke with TTL semantics
4. Distributed lock: leased prefix + CAS (etcd 3.3+ lock protocol)
5. Leader election: campaign, resign, observe (etcd v3 election API)
6. Discovery via etcd: register nodes with leased keys; watch for membership changes
7. Connection pool with leader detection and failover
8. Retry policy with exponential backoff for transient failures
9. TLS and authentication support for etcd connection
10. Implement `ClusterMembership` SPI via etcd registrations

### Final Design Decisions
- **Package:** `ssg.legoflow.service.cluster.coordination`
- **Module:** `service/cluster-coordination` (top-level under service/)
- **Dependencies:** blocks, service, cluster-core, grpc
- **Pure client** — no embedded Raft; communicates with etcd via gRPC
- **Testcontainers** for etcd in integration tests
- **Leader election** uses etcd v3 election API (not custom Raft)

### Implementation Details
- `EtcdClient` — connection pool; leader detection; auto-reconnect
- `EtcdConfig` — endpoints, auth, TLS, timeouts, retry
- `EtcdKVStore` — put, get, delete, range, txn
- `EtcdLease` — grant, keep-alive, revoke
- `EtcdSession` — lease + keep-alive for distributed locks
- `EtcdLock` — distributed mutex via leased CAS
- `EtcdElection` — leader election via CAS on leased keys
- `EtcdWatcher` — streaming watch with ordering
- `EtcdDiscovery` — ClusterMembership via etcd registrations
- `EtcdTransaction` — compare-and-swap, compare-and-delete
- `RaftLeaderElection` — Raft terminology wrapper for election API
- `RaftLogEntry` — log entry type for election context

### Test Coverage
- `EtcdKVStoreTest` — put, get, delete, range
- `EtcdLeaseTest` — grant → expiry → keys deleted
- `EtcdLockTest` — single holder; contended FIFO; crash release
- `EtcdElectionTest` — single leader; resign → new election
- `EtcdWatcherTest` — ordered notifications; gap detection
- `EtcdTransactionTest` — CAS correctness
- `EtcdDiscoveryTest` — registration; watch for changes
- `EtcdClientIntegrationTest` — failover between endpoints
- `RaftLeaderElectionTest` — election semantics

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~15000 |
| Agent tool calls | ~20 |
| Agent wall time | ~30 min |
| Files created/modified | ~30 |
| Lines added/removed | +1800 / -0 |
| Tests added | ~9 (total: ~9) |
