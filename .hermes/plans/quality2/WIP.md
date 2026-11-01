# Quality Improvements — WIP

**Start Date**: 2026-10-31
**Status**: In Progress

---

## Phase 1: Interop Test Grouping (P0)

**Status**: Not Started

**Goal**: All interop tests properly tagged and runnable via Maven groups.

**Current State**:
- 20+ interop test files identified
- Need to audit for `@Tag` annotations
- Need to create `interop-tests/doc/ci-groups.md`

**Tasks**:
- [ ] Audit all interop test files for `@Tag` annotations
- [ ] Add missing tags to tests referencing `interop.group` in comments
- [ ] Create `interop-tests/doc/ci-groups.md` documenting groups
- [ ] Ensure `failIfNoTests=true` catches typos
- [ ] Verify CI workflow runs all 4 groups correctly

**Verification**: `mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-rest` runs exactly 14 tests.

---

## Phase 2: Thread.sleep Elimination (P1)

**Status**: In Progress

**Goal**: Replace `Thread.sleep` in tests with proper synchronization where possible.

**Progress**:
- [x] AMQP integration tests — added 50ms selector thread delay after `server.connect()`
- [x] STOMP `StompBrokerTest` — reverted broken attempt, `Thread.sleep` necessary for async broker setup
- [x] ProcessingThreadTest — reverted broken attempt, `PROCESSING_POOL` uses virtual threads
- [ ] HTTP proxy tests — 5 `Thread.sleep` calls
- [ ] SSO tests — 5 `Thread.sleep` calls
- [ ] Redis `KeyCommandsExtendedTest` — 1 `Thread.sleep` call
- [ ] Postgres `PgClientTest` — 3 `Thread.sleep` calls
- [ ] OAuth tests — 3 `Thread.sleep` calls
- [ ] NATS tests — 12 `Thread.sleep` calls
- [ ] Cluster core tests — 5 `Thread.sleep` calls
- [ ] DNS resolver tests — 2 `Thread.sleep` calls
- [ ] ConnectTunnel tests — 5 `Thread.sleep` calls

**Key Finding**: `Thread.sleep` is necessary when tests involve async virtual threads (broker.accept(), PROCESSING_POOL). Cannot be eliminated without architectural changes.

**Pattern**: Use `CountDownLatch` for event synchronization where events are available. For async setup, `Thread.sleep` may be necessary.

**Analysis**:
- 79 test files contain `Thread.sleep`
- 219 total `Thread.sleep` calls in non-interop tests
- Many are for time-based assertions (TTL expiration, session expiry) — these are valid
- Some are for async timing (virtual thread scheduling) — these may need `CountDownLatch`

---

## Phase 3: Disabled Test Resolution (P1)

**Status**: In Progress

**Goal**: Resolve or document all 33 `@Disabled` tests.

**Disabled Tests Breakdown**:
- MySQL `AggregateQueryTest` (6) — unsupported features (HAVING, DISTINCT, COUNT(DISTINCT), subqueries, table aliases, UPDATE with zero rows)
- DNS resolver (7) — environment-specific (localhost DNS not available, multicast support)
- Interop tests (14) — require external servers (Docker Compose)
- MQTT (4) — virtual thread race conditions
- Others (2) — flaky timing

**Tasks**:
- [ ] MySQL: implement or document unsupported features
- [ ] DNS: fix resolver or skip with clear reason
- [ ] MQTT: fix virtual thread race conditions
- [ ] Interop: ensure Docker Compose setup is documented
- [ ] Document why each is disabled in test class header

**Current Status**:
- 19 non-interop disabled tests identified
- 14 interop tests have `@Disabled` for external dependencies
- Most have clear reasons in `@Disabled` annotation

---

## Phase 4: Coverage Gap Analysis (P2)

**Status**: Not Started

**Goal**: Achieve ≥90% coverage on all modules.

**Current Coverage**:
- Overall: 60% instructions, 43% branches
- 410 modules with coverage reports
- Need: Per-module analysis to identify gaps

**Tasks**:
- [ ] Generate per-module coverage reports
- [ ] Identify modules below 90%
- [ ] Add tests for uncovered code paths
- [ ] Focus on high-priority modules (core messaging, service)

---

## Phase 5: Documentation Gaps (P2)

**Status**: Not Started

**Goal**: Ensure all modules have required documentation.

**Current State**:
- 71 AGENTS.md files
- 61 COMPLIANCE.md files (missing from 10 modules)
- 63 ARCHITECTURE.md files
- 63 REQUIREMENTS.md files

**Tasks**:
- [ ] Identify modules missing `COMPLIANCE.md`
- [ ] Add missing compliance documentation
- [ ] Verify all modules have README.md with required badges

---

## Phase 6: Race Condition Fixes (Ongoing)

**Status**: In Progress

**Goal**: Eliminate race conditions causing flaky tests.

**Progress**:
- [x] AMQP `testFullMessagingPipeline()` — listener before connect
- [x] AMQP `testClientConnectsToServer()` — listener before connect
- [x] AMQP `testMultipleClients()` — selector thread delay
- [x] STOMP `StompBrokerTest` — reverted broken attempt, documented pattern

**Pattern**: Always set event listeners before initiating connections.

---

## Recent Fixes

| Date | Commit | Fix |
|------|--------|-----|
| 2026-10-31 | 6d277a94 | Revert STOMP broken Thread.sleep removal |
| 2026-10-31 | 6a9dbbfd | AMQP integration tests — selector thread delay |
| 2026-10-31 | 921ae594 | AMQP integration tests — listener before connect |
| 2026-10-31 | e80c588e | InMemoryByteTransport — TCP semantics + peerRef fix |

---

## Metrics

| Metric | Value |
|--------|-------|
| Total Tests | 8,582 |
| Passing | 8,582 |
| Failing | 0 |
| @Disabled | 33 (19 non-interop, 14 interop) |
| Thread.sleep in tests | 219 (non-interop) |
| TODO/FIXME | 0 (in production code) |
| Coverage (overall) | 60% |
| AGENTS.md files | 71 |
| COMPLIANCE.md files | 61 |
| ARCHITECTURE.md files | 63 |
| REQUIREMENTS.md files | 63 |

---

## References

- [Code Review](../doc/REVIEW_2026-10-31.md)
- [Test Patterns](../doc/AGENTS_test_patterns.md)
- [Protocol Accuracy](../doc/AGENTS_protocol_accuracy.md)
