
# Lego Flow Cluster Core — Membership, Events, Lifecycle

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-125_passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.2.0-SNAPSHOT-blue.svg)]()

Foundational abstractions for multi-node clustering in the Lego Flow framework. Provides cluster membership SPI, event model, health checking, failure detection, and consistent hashing used by all higher-level cluster protocols.

## Overview

This module defines the core data model and SPIs that every cluster protocol implementation depends on. It does not implement a specific transport or protocol — instead it provides the abstractions that concrete protocols (DNS-SD, etcd, gRPC, NATS) implement through the `ClusterMembership` interface.

```
Application
  -> ClusterManager (heartbeat, health check, failure detection)
    -> ClusterMembership SPI (node discovery, membership tracking)
      -> DnsSdDiscovery (cluster-discovery module)
      -> EtcdDiscovery (cluster-coordination module)
      -> Custom transport via ClusterTransport SPI
```

## Key Abstractions

- **ClusterNode** — Immutable node descriptor (ID, host, port, role, status, metadata)
- **ClusterNodeStatus** — Lifecycle enum: `ACTIVE → SUSPECT → FAILED` (failure), `FAILED → ACTIVE` (recovery)
- **ClusterRole** — Node role: `PRIMARY`, `REPLICA`, `BOTH`
- **ClusterConfig** — Runtime configuration: heartbeat interval, failure threshold, timeouts
- **ClusterStatus** — Snapshot of cluster state (members + leader)
- **ClusterMembership** — SPI for membership management (join, leave, status, events)
- **ClusterManager** — Default implementation with heartbeat, health check, failure detection
- **ClusterTransport** — SPI for inter-node messaging
- **ClusterHealthChecker** — SPI for probing remote node health
- **ClusterEvent** — Sealed hierarchy: `NodeJoined`, `NodeLeft`, `NodeFailed`, `NodeRecovered`, `LeaderChanged`
- **ConsistentHashRing** — Ketama algorithm with configurable virtual node replicas

## Quick Start

### Create a Cluster Manager

```java
var config = ClusterConfig.builder()
    .heartbeatInterval(Duration.ofSeconds(1))
    .heartbeatFailureThreshold(3)
    .build();

var membership = new DnsSdDiscovery("my-cluster", 8001);
var manager = new ClusterManager(membership, config, new TcpHealthChecker());

manager.onEvent(event -> {
    switch (event) {
        case NodeJoined nj -> log.info("Node joined: {}", nj.node().id());
        case NodeFailed nf -> log.warn("Node failed: {}", nf.node().id());
    }
});

manager.start(new DefaultContext());
```

### Consistent Hashing

```java
var ring = new ConsistentHashRing(160); // 160 virtual replicas per node
ring.add(new ClusterNode("node-1", "host1", 8001, ClusterRole.PRIMARY,
    ClusterNodeStatus.ACTIVE, Map.of()));
ring.add(new ClusterNode("node-2", "host2", 8002, ClusterRole.PRIMARY,
    ClusterNodeStatus.ACTIVE, Map.of()));

String node = ring.getNode("some-key"); // → "node-1" or "node-2"
```

## Dependencies

- **blocks** — core DP/DF data processing framework
- **service** — service lifecycle, scoped contexts
- **network-common** — shared BER/ASN.1 codec utilities
