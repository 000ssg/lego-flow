# Phase 3 — etcd / Raft Shared State

## Module
`service/cluster-coordination`

## Goal
etcd v3 client providing distributed shared state, leader election, leases, and distributed locks. Pure client — communicates with etcd servers via gRPC. No embedded Raft consensus.

## Protocol Compliance

### etcd v3 API (gRPC Service Definition)
- `KV`: Range, Put, DeleteRange, Txn, Compact
- `Watch`: Watch (server-streaming)
- `Lease`: LeaseTimeToLive, LeaseGrant, LeaseRevoke, LeaseKeepAlive (server-streaming)
- `Cluster`: MemberList, MemberAdd, MemberRemove
- `Auth`: Authenticate (if auth enabled)

### Raft Consensus (client perspective)
- Leader election is server-side; client uses linearizable reads and strong CAS
- Lease-based locks: lease revocation on leader change ensures lock release

## Design Decisions

### EtcdClient — Connection Management
- Connection pool to etcd endpoints (round-robin load balancing)
- Auto-reconnect with exponential backoff
- Leader detection: follows etcd's current leader endpoint

### EtcdKVStore — Key-Value Operations
- Put with optional lease attachment and prev-key check
- Get with revision-based consistency
- Watch: returns Stream of watch responses
- Txn: compare-and-swap, compare-and-delete, multi-op

### EtcdLease — TTL Management
- Grant: request lease with TTL seconds
- KeepAlive: auto-extend via streaming keep-alive
- Revoke: explicit lease revocation
- TTL expiration → all attached keys deleted

### EtcdLock — Distributed Mutex
- Implements via leased prefix keys + CAS (etcd 3.3+ lock API)
- Owner: node that owns the lease
- Uncontended lock: O(1) — single CAS with lease
- Contended lock: watch for predecessor deletion
- Automatic release: lease expiration (owner crash)

### EtcdElection — Leader Election
- Implements via etcd v3 election API (raft-backed)
- LeaderCampaign: acquire leadership with lease
- LeaderResign: release leadership
- Observe: watch for current leader
- Single-leader guarantee: linearizable CAS on leased key

## Testing Plan

### KV Store Tests (gRPC protocol spec compliance)
- `EtcdKVStoreTest`: put, get, delete, range with prefix
- `EtcdTransactionTest`: CAS succeeds on match; fails on mismatch
- `EtcdWatcherTest`: watch notifications ordered by revision; gap detection

### Lease Tests
- `EtcdLeaseTest`: grant → TTL expiry → keys deleted
- `EtcdSessionTest`: keep-alive extends lease; network interrupt → auto-recovery

### Lock Tests (etcd v3 lock protocol)
- `EtcdLockTest`: single lock holder at a time; contended lock FIFO
- Verify: crash simulation (lease expiry) → lock released → waiter acquires

### Election Tests (etcd v3 election protocol)
- `EtcdElectionTest`: single leader; leader resign → new election
- Leader fail (lease miss) → new campaign succeeds

### Integration Tests
- `EtcdClientIntegrationTest`: connection failover between endpoints
- End-to-end: lock → do work → unlock; election → leader work → resign

> Tests use embedded etcd via testcontainers (etcd container) for integration tests.

## Demo Plan
`EtcdCoordinationDemo` — uses testcontainers etcd
1. 3 nodes connect to etcd
2. Leader election: one wins, others observe
3. Leader fails (kill process); new election
4. Distributed lock contention: 5 nodes compete; only one holds at a time

## Files to Create
```
service/cluster-coordination/pom.xml
service/cluster-coordination/doc/REQUIREMENTS.md
src/main/java/.../cluster/coordination/
  EtcdClient.java
  EtcdConfig.java
  EtcdKVStore.java
  EtcdLease.java
  EtcdSession.java
  EtcdLock.java
  EtcdElection.java
  EtcdWatcher.java
  EtcdDiscovery.java
  EtcdTransaction.java
src/main/java/.../cluster/coordination/raft/
  RaftLeaderElection.java
  RaftLogEntry.java
src/test/java/.../cluster/coordination/
  EtcdKVStoreTest.java
  EtcdLeaseTest.java
  EtcdLockTest.java
  EtcdElectionTest.java
  EtcdWatcherTest.java
  EtcdTransactionTest.java
  EtcdDiscoveryTest.java
  EtcdClientIntegrationTest.java
  RaftLeaderElectionTest.java
demos/src/main/java/.../cluster/
  EtcdCoordinationDemo.java
```
