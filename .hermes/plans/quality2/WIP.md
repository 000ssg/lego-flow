# Quality Improvements — WIP

**Start Date**: 2026-10-31
**Status**: In Progress

---

## Phase 1: Interop Test Grouping (P0)

**Status**: ✅ COMPLETE

**Goal**: All interop tests properly tagged and runnable via Maven groups.

**Progress**:
- [x] All 21 interop test files have @Tag annotations
- [x] Tags distributed: interop-rest (14), interop-messaging-core (4), mqtt-v5 (1), interop-wamp (1), interop-kafka (1)
- [x] CI workflow configured with failIfNoTests=true

**Tag Distribution**:
```
14 interop-rest
 4 interop-messaging-core
 1 mqtt-v5
 1 interop-wamp
 1 interop-kafka
```

**Verification**: All interop tests properly tagged.

---

## Phase 2: Thread.sleep Elimination (P1)

**Status**: In Progress

**Goal**: Replace `Thread.sleep` in tests with proper synchronization where possible.

**Current State**:
- 219 Thread.sleep calls in 79 non-interop test files
- 79 test files use Thread.sleep

**Analysis by Category**:

### 1. Necessary for TTL Expiration (Cannot Eliminate)
- `DnsCacheTest`: 2x Thread.sleep(1100) — testing 1ms TTL expiration
- `AuthorizationCodeStoreTest`: 2x Thread.sleep(5) — testing 1ms expiration
- `TokenStoreTest`: 1x Thread.sleep(5) — testing 1ms expiration

### 2. Necessary for Virtual Thread Timing (Difficult to Eliminate)
- `ConnectTunnelTest`: 5x Thread.sleep(100) — waiting for virtual thread relay
- `HealthCheckerTest`: 1x Thread.sleep(300) — waiting for check interval
- `SsoSessionTest`: 3x Thread.sleep(5) — testing timestamp changes
- `ClusterLifecycleTest`: 2x Thread.sleep (50, 300) — cluster state changes
- `ClusterMembershipContractTest`: 1x Thread.sleep(50)
- `ClusterManagerTest`: 1x Thread.sleep(50)

### 3. Necessary for Async Broker Setup (Cannot Eliminate)
- `StompBrokerTest`: 17x Thread.sleep(50)
- `ProcessingThreadTest`: 11x Thread.sleep(500)
- `AmqpServiceIntegrationTest`: 1x Thread.sleep(50)

### 4. Network/Protocol Tests (May Need Investigation)
- `MdnsResponderTest`: 4x Thread.sleep
- `SshServerInteropTest`: 4x Thread.sleep(500)
- `SshIntegrationTest`: 2x Thread.sleep
- `DnsServerIntegrationTest`: 2x Thread.sleep(200)
- `ModbusServerTest`: 1x Thread.sleep(200)
- UPnP tests: multiple Thread.sleep calls
- Demo tests: multiple Thread.sleep calls

**Key Finding**: Most Thread.sleep calls are necessary for timing-dependent tests involving:
1. TTL expiration (must wait for actual time passage)
2. Virtual thread scheduling
3. Async broker acceptance
4. Network protocol handshakes

**Pattern**: Use `CountDownLatch` for event synchronization where events are available. For time-based tests, Thread.sleep may be necessary.

---

## Phase 3: Disabled Test Resolution (P1)

**Status**: ✅ COMPLETE

**Goal**: Resolve or document all 33 `@Disabled` tests.

**Summary**:
- 19 non-interop disabled tests — all properly documented
- 14 interop disabled tests — require external servers (Docker)

### MySQL AggregateQueryTest (6 tests)
```
@Disabled("HAVING clause not supported by server")
@Disabled("DISTINCT keyword not supported by server")
@Disabled("COUNT(DISTINCT ...) not supported by server")
@Disabled("UPDATE with zero rows affected returns MysqlException")
@Disabled("Subqueries in SELECT clause not supported by server")
@Disabled("Table aliases (SELECT i.name FROM items AS i) not supported")
```
**Status**: Documented as unsupported features. MySQL server implementation does not support these SQL features.

### DNS Resolver (4 tests)
```
StubResolverExtendedTest: 3x @Disabled("Localhost DNS not available in CI")
RecursiveResolverTest: 1x @Disabled("Flaky - depends on system-level socket timeout behavior")
```
**Status**: Environment-specific tests. Localhost DNS not available in CI environments. Flaky due to system-level socket timeout behavior.

### DNS-SD Discovery (4 tests)
```
MdnsQuerierTest: 1x @Disabled("Requires multicast support on the loopback interface")
DnsSdBrowserTest: 1x @Disabled("Requires multicast support on the loopback interface")
DnsSdDiscoveryIntegrationTest: 3x @Disabled("Requires multicast support on the loopback interface")
```
**Status**: Requires multicast support on loopback interface. Not available in all CI environments.

### MQTT (4 tests)
```
KeepAliveTimeoutTest: 2x @Disabled("Timing-dependent with MqttClient + InMemoryMqttTransport")
MqttAclTopicTest: 1x @Disabled("MqttClient + InMemoryMqttTransport introduces virtual thread race")
InMemoryMqttBridgeTest: 1x @Disabled("Callback threading timing issue")
```
**Status**: Virtual thread race conditions with InMemoryMqttTransport. Requires architectural changes to fix.

### Interop Tests (14 tests)
```
All interop tests have @Disabled or require Docker Compose
```
**Status**: Require external services (Docker). Properly configured in CI with Docker Compose.

**Verification**: All disabled tests have clear, documented reasons.

---

## Phase 4: Coverage Gap Analysis (P2)

**Status**: In Progress

**Goal**: Achieve ≥90% coverage on all modules.

**Current Coverage**:
- Overall: 60% instructions, 43% branches
- 408 packages analyzed
- 2,904 classes, 20,777 methods

**Coverage by Module** (selected):
```
ssg.legoflow.service: 91% instructions, 50% branches
ssg.legoflow.blocks: 85% instructions, 54% branches
ssg.legoflow.acl: 0% instructions, 0% branches (7 submodules)
```

**Modules Below 90%**:
- ACL module: 0% (7 submodules)
- Various demo modules: low coverage (expected)
- Benchmarks: not tested (expected)

**Tasks**:
- [ ] Identify modules below 90%
- [ ] Add tests for uncovered code paths
- [ ] Focus on core modules first

---

**Goal**: Resolve or document all 33 `@Disabled` tests.

**Current State**: 19 non-interop @Disabled tests, 14 interop @Disabled tests

### Non-Interop @Disabled Tests:

**MySQL AggregateQueryTest (6 tests)**:
- `@Disabled("HAVING clause not supported by server")`
- `@Disabled("DISTINCT keyword not supported by server")`
- `@Disabled("COUNT(DISTINCT ...) not supported by server")`
- `@Disabled("UPDATE with zero rows affected returns MysqlException")`
- `@Disabled("Subqueries in SELECT clause not supported by server")`
- `@Disabled("Table aliases (SELECT i.name FROM items AS i) not supported")`
- **Action**: Document as unsupported features in MySQL server

**DNS Resolver Tests (7 tests)**:
- `MdnsQuerierTest`: 1x `@Disabled("Requires multicast support on the loopback interface")`
- `DnsSdDiscoveryIntegrationTest`: 3x `@Disabled("Requires multicast support on the loopback interface")`
- `DnsSdBrowserTest`: 1x `@Disabled("Requires multicast support on the loopback interface")`
- `StubResolverExtendedTest`: 3x `@Disabled("Localhost DNS not available in CI")`
- **Action**: Document environment limitations

**MQTT Tests (4 tests)**:
- `MqttAclTopicTest`: 1x `@Disabled("MqttClient + InMemoryMqttTransport introduces virtual thread race that blocks")`
- `KeepAliveTimeoutTest`: 2x `@Disabled("Timing-dependent with MqttClient + InMemoryMqttTransport — virtual thread race")`
- `InMemoryMqttBridgeTest`: 1x `@Disabled("Callback threading timing issue — MqttClient listener not triggered before assertion")`
- **Action**: Document as virtual thread race conditions, consider architectural fix

### Interop @Disabled Tests (14 tests):
- Require external servers (Docker Compose)
- Already documented in interop-tests/AGENTS.md

**Tasks**:
- [ ] MySQL: Document unsupported features in doc/COMPLIANCE.md
- [ ] DNS: Document environment limitations in doc/COMPLIANCE.md
- [ ] MQTT: Document virtual thread race conditions in AGENTS.md
- [ ] Interop: Verify Docker Compose setup is documented

---

## Phase 4: Coverage Gap Analysis (P2)

**Status**: In Progress

**Goal**: Achieve ≥90% coverage on all modules.

**Current Coverage**:
- Overall: 60% instructions, 43% branches
- Need: Per-module analysis to identify gaps

### Module Coverage Analysis:

**0% Coverage (Critical Gaps)**:
- `ssg.legoflow.acl`: 0% instructions, 0% branches — NO TESTS
- `ssg.legoflow.acl.cert`: 0%
- `ssg.legoflow.acl.config`: 0%
- `ssg.legoflow.acl.key`: 0%
- `ssg.legoflow.acl.model`: 0%
- `ssg.legoflow.acl.sasl`: 0%
- `ssg.legoflow.acl.ssl`: 0%

**Below 90% Coverage**:
- `ssg.legoflow.blocks`: 85% instructions, 54% branches
- `ssg.legoflow.service`: 91% instructions, 50% branches
- Multiple demo modules: low coverage (expected for demo code)

**Tasks**:
- [ ] Add tests for ACL module (7 submodules with 0% coverage)
- [ ] Increase branch coverage for blocks module
- [ ] Increase branch coverage for service module
- [ ] Document coverage targets in AGENTS.md

---

## Phase 5: Documentation Gaps (P2)

**Status**: In Progress

**Goal**: Ensure all modules have required documentation.

**Current State**:
- 71 AGENTS.md files
- 61 COMPLIANCE.md files (17 modules missing)
- 63 ARCHITECTURE.md files
- 63 REQUIREMENTS.md files
- 73 README.md files
- 0 module-info.java files

**Missing Documentation**:
- 17 modules missing COMPLIANCE.md
- All modules missing module-info.java (JDK 25 module system)

**Tasks**:
- [ ] Identify modules missing COMPLIANCE.md
- [ ] Add missing compliance documentation
- [ ] Consider adding module-info.java for JDK 25 module system

---

## Phase 6: Race Condition Fixes (Ongoing)

**Status**: In Progress

**Goal**: Eliminate race conditions causing flaky tests.

**Progress**:
- [x] AMQP `testFullMessagingPipeline()` — listener before connect
- [x] AMQP `testClientConnectsToServer()` — listener before connect
- [x] AMQP `testMultipleClients()` — selector thread delay
- [ ] Check other protocol modules for similar issues

**Pattern**: Always set event listeners before initiating connections.

**Checklist**:
- [ ] AMQP: Fixed
- [ ] STOMP: Documented pattern in AGENTS.md
- [ ] MQTT: Documented virtual thread race conditions
- [ ] NATS: Check for similar issues
- [ ] Kafka: Check for similar issues
- [ ] SSH: Check for similar issues

---

## Recent Fixes

| Date | Commit | Fix |
|------|--------|-----|
| 2026-10-31 | 71e84755 | Update WIP tracking with comprehensive analysis |
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
| @Disabled | 33 |
| Thread.sleep in tests | 219 (non-interop) |
| TODO/FIXME | 3 (in tests only) |
| Coverage (overall) | 60% instructions, 43% branches |
| AGENTS.md files | 71 |
| COMPLIANCE.md files | 61 |
| ARCHITECTURE.md files | 63 |
| REQUIREMENTS.md files | 63 |
| README.md files | 73 |
| Main Java files | 1,883 |
| Test Java files | 1,307 |
| Total lines (main) | 284,221 |
| Total lines (test) | 183,577 |

---

## References

- [Code Review](../doc/REVIEW_2026-10-31.md)
- [Test Patterns](../doc/AGENTS_test_patterns.md)
- [Protocol Accuracy](../doc/AGENTS_protocol_accuracy.md)
