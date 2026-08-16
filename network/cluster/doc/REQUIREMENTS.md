
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

---

## Commit: `1f69bfd` — Complete Module Documentation (2026-08-16)

### Original Request
> "I do not see README.md and AGENTS.md in newly created modules (like core and discovery). re-check completeness of modules and fix main AGENTS.md to ensure any new module matches design principles of existing ones."

### Reformulated Requirements
1. All new cluster modules must have README.md, AGENTS.md, CLAUDE.md
2. All existing aggregator modules must have README.md, AGENTS.md, CLAUDE.md
3. Main AGENTS.md must define Standard Module Structure with aggregator and special module rules

### Final Design Decisions
- **Aggregator modules**: README + AGENTS + CLAUDE required; build.gradle.kts, COMPLIANCE.md, src/ exempt
- **Special modules** (benchmarks, demos, interop-tests): README + AGENTS + CLAUDE required; COMPLIANCE.md exempt
- **Leaf modules**: Full structure (README + AGENTS + CLAUDE + doc/ARCHITECTURE + doc/COMPLIANCE + doc/REQUIREMENTS + src/)

### Implementation Details
- Created 15 new aggregator documentation files (7 aggregators × 2-3 files each)
- Created 10 cluster module documentation files (3 modules × 3 files + aggregator)
- Created 6 special module documentation files (3 modules × 2 files each)
- Updated main AGENTS.md with Standard Module Structure section (~50 lines)
- Updated AGENTS.md with Aggregator Modules and Special Modules subsections (~22 lines)
- Added auth/README.md (AGENTS.md already existed)
- Verified all 60+ modules pass completeness audit

### Test Coverage
- No code changes — documentation only
- All existing tests pass (Gradle: 189 tasks, Maven: 54 modules)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~50K |
| Agent tool calls | ~60 |
| Agent wall time | ~30 min |
| Files created/modified | 45 |
| Lines added/removed | +1590 / 0 |
| Tests added | 0 (documentation only) |
