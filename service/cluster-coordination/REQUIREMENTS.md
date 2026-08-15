# Cluster Coordination — etcd/Raft — Requirements & Design

## Commit: (TBD) — etcd/Raft Shared State, Leader Election, Distributed Locks

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
- **Dependencies:** blocks, service, cluster-core
- **Pure client** — no embedded Raft; communicates with etcd via gRPC
- **Testcontainers** for etcd in integration tests
- **Leader election** uses etcd v3 election API (not custom Raft)
- **SPI-level simulation** — in-memory store for testing without real etcd servers
- **Leader.toString()** parsed via `parseLeader()` for observe() consistency

### Implementation Details
- `EtcdConfig` — endpoints, auth, TLS, timeouts, retry (record + builder)
- `EtcdClient` — connection pool; leader detection; auto-reconnect
- `EtcdKVStore` — put, get, delete, range, txn, revision tracking
- `EtcdLease` — grant, keep-alive, revoke, TTL management
- `EtcdSession` — lease + keep-alive for distributed locks
- `EtcdLock` — distributed mutex via leased CAS with wait-for-release
- `EtcdElection` — leader election via CAS on leased keys; implements AutoCloseable
- `EtcdWatcher` — polling-based watch with ordering by revision
- `EtcdDiscovery` — `ClusterMembership` SPI via etcd registrations
- `EtcdTransaction` — compare-and-swap, multi-op builder with thenPut/thenDelete
- `RaftLeaderElection` — Raft terminology wrapper (campaign, resign, observeLeader)
- `RaftLogEntry` — log entry record with EntryType enum (NORMAL, NOOP, CONFIG_CHANGE)

### Test Coverage
- `EtcdConfigTest` — 10 tests (builder, validation, defaults)
- `EtcdKVStoreTest` — 19 tests (put, get, delete, range, revision, close)
- `EtcdLeaseTest` — 16 tests (grant, keep-alive, revoke, TTL expiry)
- `EtcdLockTest` — 11 tests (acquire, release, contention, isHeld)
- `EtcdElectionTest` — 18 tests (campaign, resign, observe, leaderChanged)
- `EtcdWatcherTest` — 12 tests (start, events, close, revision tracking)
- `EtcdTransactionTest` — 14 tests (CAS, thenPutWithLease, thenDelete, multi-op)
- `EtcdDiscoveryTest` — 18 tests (register, status, watch, leave, ClusterMembership)
- `EtcdClientIntegrationTest` — 11 tests (failover, connection lifecycle)
- `EtcdSessionTest` — 12 tests (create, keep-alive, close, lease binding)
- `RaftLeaderElectionTest` — 16 tests (campaign, resign, term, observeLeader)
- `RaftLogEntryTest` — 11 tests (construction, of(), noop(), validation)
- **Total: 168 tests, all passing**

### Demo
`EtcdCoordinationDemo` — exercises all 7 primitives:
1. KV Store: put, get, delete, range queries, revision tracking
2. Transactions: CAS succeeds on match; fails on stale data
3. Leader Election: A wins, B/C lose; A resigns, B wins; observer updates
4. Distributed Lock: sequential lock/unlock by two nodes
5. Watch & React: prefix watch detects config changes in real-time
6. Cluster Discovery: node registration, membership status, node leave
7. Raft Log: entry creation with CONFIG_CHANGE, NORMAL, NOOP types
8. Session: lease creation, key protection, lease revocation on close

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~25000 |
| Agent tool calls | ~40 |
| Agent wall time | ~45 min |
| Files created | 26 (12 src + 12 test + 1 demo + 1 pom) |
| Lines added | +3888 |
| Tests added | 168 |
