# Phase 7 — HTTP Cache Coherence

## Modules
`web/http` (extension) + `web/http-proxy` (extension)

## Goal
Cross-node cache invalidation for web server clusters. When content changes on one node, cached responses on all other nodes are invalidated.

## Design

### CacheCoherenceFeature
- `HttpFeature` for server-side coherence
- On PUT/POST/DELETE: publishes invalidation event for affected paths
- Invalidation bus: NATS ClusterBus (Phase 5) or gRPC (Phase 4)
- Configurable scope: path prefix matching for broad invalidation

### HttpCacheInvalidator
- Intercepts write operations (PUT, POST, DELETE)
- Extracts affected paths from request URI
- Publishes `CacheInvalidation` event: `{paths: ["/api/users/*"], source: "node-A"}`
- Receives invalidation events → removes matching entries from local cache

### ClusterHealthMonitor
- Periodic HTTP health endpoint: `GET /health` between cluster nodes
- Reports: uptime, memory, request rate, cache hit ratio
- On failure: notifies http-proxy to remove node from pool

### ProxyClusterConfig
- http-proxy extension: cluster backend group
- Health check: periodic probe to each backend
- Failover: automatic removal of unhealthy backends
- Recovery: re-add backends on health recovery

## Testing Plan
- `CacheCoherenceFeatureTest`: PUT → invalidation event published; received → cache entry removed
- `HttpCacheInvalidatorTest`: path wildcard matching; multiple path batch
- `ClusterHealthMonitorTest`: healthy → included; unhealthy → excluded; recovery → re-included

## Demo Plan
`CacheCoherenceDemo` — 3-node web cluster
1. All nodes cache response for `/api/data`
2. Node A receives PUT `/api/data` → publishes invalidation
3. Nodes B and C receive → invalidate cache
4. Next GET on B/C → fresh fetch from origin

## Files to Create
```
web/http/src/main/java/.../http/cluster/
  CacheCoherenceFeature.java
  HttpCacheInvalidator.java
  CacheCoherenceConfig.java
web/http-proxy/src/main/java/.../http/proxy/cluster/
  ClusterHealthMonitor.java
  ProxyClusterConfig.java
src/test/java/.../http/cluster/
  CacheCoherenceFeatureTest.java
  HttpCacheInvalidatorTest.java
src/test/java/.../http/proxy/cluster/
  ClusterHealthMonitorTest.java
```
