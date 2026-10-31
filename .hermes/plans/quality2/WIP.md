# Quality Improvements — WIP

**Start Date**: 2026-10-31
**Status**: In Progress

---

## Phase 1: Interop Test Grouping (P0)

**Status**: Not Started

**Goal**: All interop tests properly tagged and runnable via Maven groups.

**Tasks**:
- [ ] Add `@Tag("interop-mqtt-v5")` to `MqttV5FeatureTest`
- [ ] Create `.github/workflows/interop-groups.md` documenting group execution
- [ ] Add `interop-kafka` group to CI workflow

---

## Phase 2: Thread.sleep Elimination (P1)

**Status**: In Progress

**Goal**: Replace `Thread.sleep` in tests with proper synchronization primitives.

**Progress**:
- [x] AMQP integration tests — added 50ms selector thread delay after `server.connect()`
- [ ] MySQL `AggregateQueryTest` — 6 disabled tests, 6 TODOs
- [ ] DNS resolver tests — 4 disabled tests, 4 TODOs
- [ ] HTTP proxy tests — 5 `Thread.sleep` calls
- [ ] SSO tests — 5 `Thread.sleep` calls
- [ ] Redis `KeyCommandsExtendedTest` — 1 `Thread.sleep` call
- [ ] Postgres `PgClientTest` — 3 `Thread.sleep` calls
- [ ] OAuth tests — 3 `Thread.sleep` calls

**Pattern**: Use `CountDownLatch` or `AmqpEventListener.latchOnFirst()` for event synchronization.

---

## Phase 3: Disabled Test Resolution (P1)

**Status**: Not Started

**Goal**: Resolve or document all 33 `@Disabled` tests.

**Disabled Tests**:
- MySQL `AggregateQueryTest` (6) — unsupported features
- DNS resolver (7) — environment-specific
- Interop tests (14) — require external servers
- MQTT (4) — virtual thread race conditions

**Tasks**:
- [ ] MySQL: implement or document unsupported features
- [ ] DNS: fix resolver or skip with clear reason
- [ ] MQTT: fix virtual thread race conditions
- [ ] Interop: ensure Docker Compose setup is documented

---

## Phase 4: Coverage Gap Analysis (P2)

**Status**: Not Started

**Goal**: Achieve ≥90% coverage on all modules.

**Current Coverage**:
- Overall: 60% instructions, 43% branches
- Need: Per-module analysis to identify gaps

**Tasks**:
- [ ] Generate per-module coverage reports
- [ ] Identify modules below 90%
- [ ] Add tests for uncovered code paths

---

## Phase 5: Documentation Gaps (P2)

**Status**: Not Started

**Goal**: Ensure all modules have required documentation.

**Current State**:
- 63/65 modules have `doc/` directory
- 61/65 modules have `COMPLIANCE.md`
- 63/65 modules have `ARCHITECTURE.md`
- 63/65 modules have `REQUIREMENTS.md`
- 71/65 modules have `AGENTS.md` (some have extras)

**Tasks**:
- [ ] Identify modules missing `COMPLIANCE.md`
- [ ] Add missing compliance documentation

---

## Phase 6: Race Condition Fixes (Ongoing)

**Status**: In Progress

**Goal**: Eliminate race conditions causing flaky tests.

**Progress**:
- [x] AMQP `testFullMessagingPipeline()` — listener before connect
- [x] AMQP `testClientConnectsToServer()` — listener before connect
- [x] AMQP `testMultipleClients()` — selector thread delay
- [ ] Monitor for new race conditions

**Pattern**: Always set event listeners before initiating connections.

---

## Recent Fixes

| Date | Commit | Fix |
|------|--------|-----|
| 2026-10-31 | 6a9dbbfd | AMQP integration tests — selector thread delay |
| 2026-10-31 | 921ae594 | AMQP integration tests — listener before connect |
| 2026-10-31 | e80c588e | InMemoryByteTransport — TCP semantics + peerRef fix |

---

## Metrics

| Metric | Value |
|--------|-------|
| Total Tests | 8,582 |
| Passing | 8,581 |
| Failing | 0 |
| @Disabled | 33 |
| Thread.sleep in tests | 254 |
| TODO/FIXME | 10 |
| Coverage (overall) | 60% |
