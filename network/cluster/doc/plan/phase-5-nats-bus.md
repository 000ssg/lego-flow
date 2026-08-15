# Phase 5 — NATS Cluster Bus

## Module
`messaging/nats` — new `cluster` subpackage (extension)

## Goal
Cluster messaging patterns on top of NATS: node-addressed pub/sub, fan-out broadcast, ordered invalidation, and membership tracking.

## Protocol Compliance

### NATS Protocol
- Publish: `PUB subject reply-to payload-size payload`
- Subscribe: `SUB subject group`
- Request/Reply: publish with reply-to subject; server matches to subscriber
- JetStream: persistent streaming with ordering guarantees

### Subject Conventions (NATS)
- Subject hierarchy: `cluster.<service>.<event>`
- Wildcard subscriptions: `cluster.mySvc.>` for all events
- Node-scoped: `cluster.mySvc.node.<nodeId>.ready`
- Reply-to pattern for node-targeted messages

## Design Decisions

### NatsClusterBus
- Publish to `cluster.<service>.<event>` pattern
- Supports: broadcast (publish), point-to-point (publish with node-targeted subject)
- Auto-subscribes to membership events
- Uses NATS queue groups for exclusive processing per event type

### ClusterSubjectRegistry
- Maps `(eventType, service)` → subject string
- Configurable prefix: default `cluster`
- Resolves: `resolve("invalidate", "web")` → `cluster.web.invalidate`

### NatsClusterMembership
- Publishes heartbeat to `_presence.legoflow` subject (NATS convention)
- Subscribes to presence subject; tracks member liveness
- Timeout: member marked dead after 2 missed heartbeats
- Integrates with `ClusterMembership` SPI

### OrderedInvalidation
- Uses JetStream ordered consumer for delivery guarantees
- Publishes invalidation events with sequence numbers
- Consumers process in order; skips already-processed sequences

## Testing Plan

### Bus Tests
- `NatsClusterBusTest`: publish/subscribe with correct subject routing
- `ClusterSubjectRegistryTest`: resolve event types to subjects
- `ClusterBroadcastTest`: N-1 delivery (sender excluded)

### Membership Tests
- `NatsClusterMembershipTest`: heartbeat publish; member tracking
  - Join: heartbeat starts; others receive
  - Leave: final heartbeat; others receive goodbye
  - Missed heartbeat (2× timeout) → member marked dead

### Ordered Invalidation Tests
- `OrderedInvalidationTest`: sequence preservation under concurrent publish
  - 100 events published concurrently → all delivered in order
  - Consumer restart → resumes from last processed sequence

### Integration Tests
- `NatsClusterIntegrationTest`: 3-node cluster with NATS server (testcontainers)
  - Broadcast → N-1 delivery
  - Node failure → membership update
  - Ordered invalidation across restart

## Demo Plan
`NatsClusterBusDemo` — 4 nodes, NATS server
1. Nodes auto-register via presence
2. Node A broadcasts config update → B, C, D receive
3. Node B sends targeted message to D
4. Ordered invalidation sequence (1-10) → all nodes process in order
5. Kill node C → others detect; C restarts → re-registers

## Files to Create
```
messaging/nats/src/main/java/.../nats/cluster/
  NatsClusterBus.java
  ClusterSubjectRegistry.java
  NatsClusterMembership.java
  ClusterBroadcast.java
  NodeTargetedMessage.java
  OrderedInvalidation.java
src/test/java/.../nats/cluster/
  NatsClusterBusTest.java
  ClusterSubjectRegistryTest.java
  NatsClusterMembershipTest.java
  ClusterBroadcastTest.java
  OrderedInvalidationTest.java
  NatsClusterIntegrationTest.java
demos/src/main/java/.../cluster/
  NatsClusterBusDemo.java
```
