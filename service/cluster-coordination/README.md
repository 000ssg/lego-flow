
# Lego Flow Cluster Coordination — etcd/Raft

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-168_passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.2.0-SNAPSHOT-blue.svg)]()

Distributed coordination primitives built on etcd and Raft consensus. Provides shared state management, leader election, distributed locking, leases, and key-value storage across cluster nodes.

## Overview

This module provides two layers of coordination:

1. **etcd Client API** — Full client for etcd's HTTP API (KV store, leases, watches, locks, elections, sessions, transactions)
2. **Raft Leader Election** — Lightweight Raft-style leader election simulation for in-process or in-cluster coordination

```
Application (leader election, config store, locks)
  -> etcd Client (KV, leases, watches, locks, elections)
    -> HTTP Transport (endpoints, auth, TLS, reconnect)
  
  -> Raft Leader Election (term-based, vote, log)
    -> RaftLogEntry (NORMAL, NOOP, CONFIG_CHANGE)
```

## Key Classes

- **EtcdClient** — Connection to etcd cluster (endpoints, leader detection, reconnect)
- **EtcdKVStore** — Key-value operations (get, put, delete, range scan)
- **EtcdLease** — TTL-based lease (grant, revoke, renew, keep-alive)
- **EtcdLock** — Distributed mutex (acquire, extend, release via revkeys)
- **EtcdElection** — Leader election (campaign, resign, leadership check)
- **EtcdSession** — Session bound to a lease (liveness tracking)
- **EtcdTransaction** — Compare-and-swap transactions (conditional put)
- **EtcdWatcher** — Key prefix watch (event stream with reconnection)
- **EtcdDiscovery** — `ClusterMembership` via etcd endpoint registrations
- **RaftLeaderElection** — Lightweight Raft leader election (term, vote, log)
- **RaftLogEntry** — Raft log entry model (term, index, type, data)

## Quick Start

### Key-Value Store

```java
var config = EtcdConfig.builder()
    .endpoints(List.of("http://localhost:2379"))
    .dialTimeout(Duration.ofSeconds(5))
    .build();

var client = new EtcdClient(config);
var store = client.getKVStore();

store.put("my-key", "my-value").join();
String value = store.get("my-key").join();  // → "my-value"
```

### Leader Election

```java
var election = client.newElection("my-service", "node-1");
election.campaign().join();  // Become leader

if (election.isLeader()) {
    // Perform leader-only work
}

election.resign().join();  // Step down
```

### Distributed Lock

```java
var lock = client.newLock("my-resource", "node-1");
lock.acquire().join();
try {
    // Critical section
} finally {
    lock.release().join();
}
```

### Watch for Changes

```java
var watcher = client.newWatcher("/config/");
watcher.watch(events -> {
    for (var event : events) {
        log.info("Config changed: {} → {}", event.key(), event.value());
    }
}).join();
```

## etcd API Compliance

- **KV Store** — Range, Put, DeleteRange with revision tracking
- **Leases** — Grant, revoke, keep-alive, TTL management
- **Locks** — Revkey-based distributed mutex with CAS
- **Elections** — Campaign, resign, leadership observation
- **Sessions** — Lease-bound session with automatic renewal
- **Transactions** — Compare-and-swap with multi-op support
- **Watches** — Prefix watches with revision-ordered events

## Design Decisions

- **etcd as primary backend** — Provides all standard coordination primitives backed by Raft consensus
- **Separate Raft simulation** — `RaftLeaderElection` for environments without etcd (demos, testing, embedded)
- **Connection lifecycle** — `EtcdClient` handles endpoint failover, leader detection, auto-reconnect
- **Lease-based liveness** — Leases and sessions are the building blocks; locks and elections built on top
- **Async API** — All operations return `CompletableFuture` for non-blocking execution
- **Testcontainers** — Integration tests use testcontainers for real etcd validation

## Dependencies

- **blocks** — core DP/DF data processing framework
- **service** — service lifecycle, scoped contexts
- **cluster-core** — membership SPI, `ClusterNode`, events
