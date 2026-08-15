# Phase 6 — Consistent Hashing + HTTP Sticky Sessions

## Modules
`network/cluster/core` (hashing) + `web/http` (sticky sessions)

## Goal
Two workload balancing mechanisms: consistent hashing for data partitioning, and HTTP sticky sessions for stateful web clusters.

---

## Part A: Consistent Hashing

### Algorithm: Ketama (memcached-style)
- Virtual node ring: each physical node mapped to N virtual nodes (default: 160)
- Hash function: CRC32 (per Ketama original) or MurmurHash3 (per modified Ketama)
- Node placement: hash(node_name:replica_index) → position on 32-bit ring
- Key lookup: hash(key) → find next clockwise virtual node

### Design Decisions
- `ConsistentHashRing`: ring data structure; O(log N) lookup via TreeMap
- `HashFunction` SPI: allows swapping CRC32, MurmurHash3, SHA-256
- `KetamaNodeAddress`: `nodeName:replicaIndex` → hash position
- Virtual replicas: configurable (10–500); default 160 per Ketama
- Redistribution bound: adding/removing 1 node redistributes ~1/N of keys (with virtual replicas)

### Testing Plan
- `ConsistentHashRingTest`: ring construction, add/remove, lookup
- `MurmurHash3Test`: verify against known test vectors (Austin Appleby reference)
- `KetamaRedistributionTest`: add node → max 10% redistribution (with 160 replicas)
- `ConsistentHasherTest`: key→node mapping consistency; rehash on membership change

---

## Part B: HTTP Sticky Sessions

### Mechanism
- Server sets `X-Session-Node` cookie (configurable name) containing node ID
- Cookie value: `nodeId` (URL-safe Base64 or hex)
- Optional TTL: cookie expires after configurable duration
- Router reads cookie → routes to indicated node
- If indicated node is down → rehash to available node → set new cookie

### Design Decisions
- `StickySessionFeature`: `HttpFeature` — follows HTTP feature system pattern
- `SessionCookieBuilder`: generates cookie per response (for server)
- `StickySessionRouter`: reads cookie from request → routes to node
- `SessionAffinityConfig`: cookie name, TTL, fallback strategy (rehash, redirect, error)

### Testing Plan
- `StickySessionFeatureTest`: feature enable/disable; cookie generation
- `SessionCookieBuilderTest`: correct Set-Cookie header; TTL formatting
- `StickySessionRouterTest`: cookie → node lookup; fallback on node failure
- Integration: browser re-connection → same node; TTL expiry → new node

## Demo Plan
`StickySessionDemo` — 3 HTTP server nodes behind proxy
1. Client connects → assigned node A → receives session cookie
2. Client re-requests → routed to node A (sticky)
3. Node A fails → client re-requests → routed to node B → new cookie
4. Consistent hashing: key "user-123" → always node A; remove A → maps to C

`ConsistentHashDemo` — 5-node ring visualization
1. Show 100 keys distributed across 5 nodes
2. Add 6th node → show redistribution percentage
3. Remove node 3 → show reassignment

## Files to Create
```
network/cluster/core/src/main/java/.../cluster/hashing/
  ConsistentHashRing.java
  HashFunction.java
  MurmurHash3.java
  ConsistentHasher.java
  KetamaNodeAddress.java
web/http/src/main/java/.../http/cluster/
  StickySessionFeature.java
  SessionCookieBuilder.java
  StickySessionRouter.java
  SessionAffinityConfig.java
src/test/java/.../cluster/hashing/
  ConsistentHashRingTest.java
  MurmurHash3Test.java
  KetamaRedistributionTest.java
  ConsistentHasherTest.java
src/test/java/.../http/cluster/
  StickySessionFeatureTest.java
  SessionCookieBuilderTest.java
  StickySessionRouterTest.java
demos/src/main/java/.../cluster/
  StickySessionDemo.java
  ConsistentHashDemo.java
```
