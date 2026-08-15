# Phase 4 — gRPC Cluster Resolver + Load Balancer

## Module
`rpc/grpc` — new `cluster` subpackage (extension)

## Goal
Client-side load balancing for gRPC: resolve service names to backend addresses via pluggable discovery, balance across backends, and health-check to exclude unhealthy nodes.

## Protocol Compliance

### gRPC Name Resolution (xDS-less)
- URI scheme: `cluster:///service-name` 
- Resolver translates to list of `EquivalentAddressGroup` (backend addresses)
- Resolution result includes attributes (metadata) for each address

### gRPC Load Balancing Policy
- Policies: `round_robin`, `pick_first`, `least_request`, `consistent_hashing`
- Each policy receives a list of subchannels (one per address)
- `pickResult()` / `updateBalancingState()` semantics per gRPC spec

### gRPC Health Checking (RFC-style)
- `grpc.health.v1.Health` service
- Check RPC: `HealthCheckRequest(service) → HealthCheckResponse(status)`
- Status: SERVING, NOT_SERVING, SERVICE_UNKNOWN

## Design Decisions

### ClusterResolver
- Pluggable `AddressSource`: DNS-SD, etcd, static list, or custom
- On address change: notifies balancer of new address list
- Attributes: each address carries metadata (node ID, weight, region)

### GrpcLoadBalancer (sealed)
- `RoundRobinBalancer`: cycles through healthy backends
- `LeastRequestBalancer`: picks backend with fewest in-flight RPCs
- `ConsistentHashBalancer`: hashes request key (e.g., session ID) to backend
- All balancers maintain subchannel pool with health status

### GrpcHealthChecker
- Periodic health check RPC on each subchannel
- Configurable interval and timeout
- Transition: SERVING → NOT_SERVING excludes backend; re-included on recovery

### GrpcClusterClient
- Factory: `GrpcClusterClient.builder().resolver(resolver).balancer(balancer).build()`
- Wires resolver → balancer → subchannels → health checker
- Returns standard `ManagedChannel` for use with generated stubs

## Testing Plan

### Resolver Tests
- `ClusterResolverTest`: address change propagation
  - Add address → balancer receives new address
  - Remove address → subchannel drained and closed
  - Update attributes → existing subchannel updated

### Balancer Tests
- `RoundRobinBalancerTest`: N requests → even distribution across N backends
- `LeastRequestBalancerTest`: with in-flight tracking, picks least-loaded backend
- `ConsistentHashBalancerTest`: same key → same backend; key distribution evenness
- Failover: backend failure → requests redirect to healthy backends

### Health Checker Tests
- `GrpcHealthCheckerTest`: periodic check; transition detection
  - SERVING → backend included
  - NOT_SERVING → backend excluded after N consecutive failures
  - Recovery → backend re-included

### Integration Tests
- `GrpcClusterClientFailoverTest`: 3 mock backends; kill one; verify failover
- `AddressSourceIntegrationTest`: DNS-SD and etcd address sources wired to resolver

## Demo Plan
`GrpcClusterDemo` — 3 gRPC backend servers
1. Client resolves via DNS-SD; discovers 3 backends
2. Sends 30 requests → even distribution
3. Kill backend B → requests redirect to A and C
4. Restart B → it rejoins the pool
5. Show health check logs

## Files to Create
```
rpc/grpc/src/main/java/.../grpc/cluster/
  ClusterResolver.java
  GrpcLoadBalancer.java (sealed)
  RoundRobinBalancer.java
  LeastRequestBalancer.java
  ConsistentHashBalancer.java
  AddressSource.java
  GrpcHealthChecker.java
  GrpcClusterClient.java
  ClusterSubchannel.java
src/test/java/.../grpc/cluster/
  ClusterResolverTest.java
  RoundRobinBalancerTest.java
  LeastRequestBalancerTest.java
  ConsistentHashBalancerTest.java
  GrpcHealthCheckerTest.java
  GrpcClusterClientFailoverTest.java
  AddressSourceIntegrationTest.java
demos/src/main/java/.../cluster/
  GrpcMicroserviceClusterDemo.java
```
