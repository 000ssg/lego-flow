# Phase 8 — Integration Demos

## Module
`demos` — new `cluster` subpackage

## Goal
End-to-end demos combining all cluster protocols into realistic, multi-component scenarios. Each demo is an executable scenario with assertions.

## Scenarios

### 1. Auto-Discovering Web Cluster
**Protocols:** DNS-SD, Sticky Sessions, Consistent Hashing, NATS Bus, Cache Coherence
- 3 HTTP servers auto-discover via mDNS
- Client uses sticky sessions for stateful routing
- Data partitioned via consistent hashing
- Cache invalidation via NATS broadcast
- **Assertions:** request routing consistent; cache coherence within 1s

### 2. gRPC Microservice Cluster
**Protocols:** DNS-SD (or etcd), gRPC Load Balancing, Health Checking
- 5 gRPC backends; client discovers via DNS-SD
- Client-side round-robin with health checking
- **Assertions:** even distribution; failover within 2s; recovery re-inclusion

### 3. Distributed Leader Election
**Protocols:** etcd/EtcdLock, etcd/Election, NATS Bus
- 3 nodes compete for leadership via etcd
- Leader publishes work assignments via NATS
- Leader fail → new election → new leader resumes
- **Assertions:** single leader at any time; election completes within 3s

### 4. Partition Tolerance
**Protocols:** DNS-SD, NATS Bus, etcd
- 5-node cluster; simulate network partition (2 vs 3)
- Majority partition: elects leader, continues operations
- Minority partition: detects partition, enters read-only mode
- Partition heals: state reconciliation
- **Assertions:** no split-brain writes; majority partition serves; recovery correct

## Testing Plan
- Each demo has a companion test class
- Tests use testcontainers for NATS, etcd
- DNS-SD tests use loopback multicast
- gRPC tests use in-process channels with mock backends
- Assertions: timing, correctness, consistency

## Demo Files
```
demos/src/main/java/.../demos/cluster/
  AutoDiscoveringWebClusterDemo.java
  GrpcMicroserviceClusterDemo.java
  DistributedLeaderElectionDemo.java
  PartitionToleranceDemo.java
  ClusterDemoRunner.java
demos/src/test/java/.../demos/cluster/
  AutoDiscoveringWebClusterDemoTest.java
  GrpcMicroserviceClusterDemoTest.java
  DistributedLeaderElectionDemoTest.java
  PartitionToleranceDemoTest.java
```
