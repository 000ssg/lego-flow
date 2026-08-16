
# Lego Flow Cluster — Multi-node Clustering Protocols

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for the cluster protocol suite in the Lego Flow framework. Provides multi-node clustering support enabling deployment of services in distributed environments.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [core](core/) | `lego-flow-cluster-core` | Membership SPI, events, lifecycle, consistent hashing |
| [discovery](discovery/) | `lego-flow-cluster-discovery` | DNS-SD/mDNS zero-config peer discovery |

## Related

- [cluster-coordination](../../service/cluster-coordination/) — etcd/Raft shared state, locks, leader election (in `service/`)
- [grpc cluster resolver](../../rpc/grpc/) — Client-side load balancing with cluster awareness
- [NATS cluster bus](../../messaging/nats/) — Cluster messaging bus with health monitoring
- [HTTP sticky sessions](../../web/http/) — Cookie-based session affinity for web clusters
- [HTTP-proxy health monitor](../../web/http-proxy/) — Backend health checking for proxy clusters

## Protocol Selection

| Functionality | Primary | Alternative |
|---------------|---------|-------------|
| Discovery | DNS-SD/mDNS, etcd | gRPC resolver |
| Shared State | etcd (Raft) | Redis, ZooKeeper |
| Inter-Node RPC | gRPC (extended) | — |
| Cluster Messaging | NATS (extended) | Redis Pub/Sub |
| Workload Balancing | Consistent Hashing, Sticky Sessions | Round-robin, Least-Request |
| Cache Coherence | NATS invalidation bus | gRPC signals |
