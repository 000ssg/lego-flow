
# Cluster Aggregator — Requirements

## Commit: be52ae0 — Cluster Protocols Design (2026-08-15)

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality"

### Reformulated Requirements
1. Investigate and select cluster protocols for: discovery, shared state, workload balancing, inter-node messaging, cache coherence
2. Create modular architecture with clean SPI boundaries
3. Split into phased implementation with clear dependencies

### Final Design Decisions
- **core** — Foundation abstractions (membership, events, hashing) as parent module
- **discovery** — DNS-SD/mDNS as zero-config discovery protocol
- **coordination** — etcd/Raft as shared state backend (separate module under service/)
- **Extended modules** — gRPC, NATS, HTTP, HTTP-proxy get cluster sub-packages

### Implementation Details
- Aggregator POM groups core and discovery sub-modules
- 8-phase implementation plan in `doc/plan/`

### Test Coverage
- Core: 125 tests
- Discovery: 102 tests
- Coordination: 168 tests (separate module)
