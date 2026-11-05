---
title: Lego Flow Quality Improvement Analysis
name: lego-flow-quality-improvement
description: Audit test quality, coverage, docs in lego-flow.
trigger: Analyzing or improving test quality, coverage, documentation, or code health in lego-flow
---

# Lego Flow Quality Improvement Analysis

Use when conducting a comprehensive quality improvement analysis of the lego-flow project. This covers interop test grouping, Thread.sleep analysis, disabled test documentation, coverage gaps, and documentation gaps.

## Phase Workflow (Execute in Order)

1. **Phase 1: Interop Test Grouping** — Audit all interop tests for `@Tag` annotations
2. **Phase 2: Thread.sleep Elimination** — Analyze Thread.sleep calls by category
3. **Phase 3: Disabled Test Resolution** — Document all @Disabled tests with reasons
4. **Phase 4: Coverage Gap Analysis** — Generate JaCoCo reports, identify modules below 90%
5. **Phase 5: Documentation Gaps** — Check for missing COMPLIANCE.md, AGENTS.md, etc.
6. **Phase 6: Race Condition Fixes** — Fix and document race conditions in tests

Track progress in `.hermes/plans/quality2/WIP.md` using WIP.md mechanism.

## Phase 1: Interop Test Grouping

**Goal**: All interop tests properly tagged and runnable via Maven groups.

**Steps**:
1. Find all interop test files:
   ```bash
   find . -name "*.java" -path "*/interop-tests/*" -exec grep -l "class.*Test" {} \;
   ```
2. Check for @Tag annotations:
   ```bash
   grep -r "@Tag" --include="*.java" ./interop-tests/src
   ```
3. Verify tag distribution:
   ```bash
   grep -r "@Tag" --include="*.java" ./interop-tests/src | awk -F'"' '{print $2}' | sort | uniq -c | sort -rn
   ```

**Expected Tags**:
- `interop-rest` — HTTP, FTP, Redis, LDAP, SSH, Telnet, etc.
- `interop-messaging-core` — AMQP, STOMP, MQTT, XMPP, NATS
- `mqtt-v5` — MQTT v5 specific features
- `interop-wamp` — WAMP protocol
- `interop-kafka` — Kafka protocol

**Verification**: All interop tests have @Tag annotations. CI workflow configured with `failIfNoTests=true`.

## Phase 2: Thread.sleep Analysis

**Goal**: Analyze all Thread.sleep calls, categorize by necessity, eliminate where possible.

**Steps**:
1. Find all Thread.sleep calls in tests:
   ```bash
   find . -name "*.java" -path "*/test/*" -exec grep -l "Thread.sleep" {} \; | wc -l
   find . -name "*.java" -path "*/test/*" -exec grep -l "Thread.sleep" {} \; | grep -v "interop-tests"
   ```
2. Count total Thread.sleep calls:
   ```bash
   find . -name "*.java" -path "*/test/*" -exec grep -l "Thread.sleep" {} \; | grep -v "interop-tests" | xargs grep -h "Thread.sleep" | wc -l
   ```
3. Categorize by use case:
   - **TTL expiration tests** — Necessary (testing timeout behavior)
   - **Virtual thread timing** — Often necessary (async broker setup, selector thread initialization)
   - **Protocol handshake waits** — May be replaceable with CountDownLatch
   - **Transport initialization** — Often necessary (InMemoryTransport async setup)

**Key Finding**: Most Thread.sleep calls are necessary for:
- TTL expiration tests (DnsCacheTest, TokenStoreTest, AuthorizationCodeStoreTest)
- Virtual thread timing (ConnectTunnelTest, HealthCheckerTest)
- Async broker setup (StompBrokerTest, ProcessingThreadTest)
- Selector thread initialization (AMQP integration tests)

**Pattern**: Use `CountDownLatch` for event synchronization where events are available. For async setup, `Thread.sleep` may be necessary.

**Report format**:
```
### 1. Necessary for TTL Expiration (Cannot Eliminate)
- `ClassName`: N x Thread.sleep(X) — testing Yms TTL expiration

### 2. Necessary for Virtual Thread Timing (Difficult to Eliminate)
- `ClassName`: N x Thread.sleep(X) — waiting for virtual thread relay
```

## Phase 3: Disabled Test Resolution

**Goal**: Resolve or document all @Disabled tests.

**Steps**:
1. Find all @Disabled tests:
   ```bash
   grep -r "@Disabled" --include="*.java" . | grep -v build | grep -v "interop-tests"
   ```
2. Categorize by reason:
   - **Unsupported features** — Server implementation doesn't support SQL feature
   - **Environment-specific** — Localhost DNS not available in CI
   - **Multicast dependency** — Requires multicast on loopback interface
   - **Virtual thread race** — InMemoryTransport timing issues
   - **Timing-dependent** — Flaky due to system-level socket timeout behavior

3. Document each disabled test with clear reason in @Disabled annotation.

**Verification**: All disabled tests have clear, documented reasons. No @Disabled without explanation.

## Phase 4: Coverage Gap Analysis

**Goal**: Achieve ≥90% coverage on all modules.

**Steps**:
1. Generate JaCoCo report:
   ```bash
   ./gradlew jacocoAggregateReport --no-daemon
   ```
2. Find coverage by module:
   ```bash
   for f in build/jacoco/aggregate/html/Lego_Flow/ssg.legoflow.*/index.html; do
     if [ -f "$f" ]; then
       name=$(basename $(dirname $f));
       pct=$(grep -oE '[0-9]+\.[0-9]+%' "$f" 2>/dev/null | head -1 | grep -oE '[0-9.]+');
       if [ -n "$pct" ]; then echo "$pct $name"; fi
     fi
   done | sort -rn
   ```
3. Identify modules below 90%:
   ```bash
   for f in build/jacoco/aggregate/html/Lego_Flow/ssg.legoflow.*/index.html; do
     if [ -f "$f" ]; then
       name=$(basename $(dirname $f));
       pct=$(grep -oE '[0-9]+\.[0-9]+%' "$f" 2>/dev/null | head -1 | grep -oE '[0-9.]+');
       if [ -n "$pct" ] && [ $(echo "$pct < 90" | bc -l 2>/dev/null || echo "0") -eq 1 ]; then
         echo "$name: $pct%"
       fi
     fi
   done | sort -t: -k2 -rn
   ```

**Critical Gaps**:
- ACL module: 0% coverage (7 submodules)
- Demo modules: Low coverage (expected)
- Benchmarks: Not tested (expected)

**Report format**:
```
| Module | Instructions | Branches |
|--------|-------------|----------|
| ssg.legoflow.acl | 0% | 0% |
| ssg.legoflow.blocks | 85% | 54% |
| ssg.legoflow.service | 91% | 50% |
```

## Phase 5: Documentation Gaps

**Goal**: Ensure all modules have required documentation.

**Steps**:
1. Count documentation files:
   ```bash
   find . -name "COMPLIANCE.md" -not -path "*/build/*" | wc -l
   find . -name "AGENTS.md" -not -path "*/build/*" | wc -l
   find . -name "ARCHITECTURE.md" -not -path "*/build/*" | wc -l
   find . -name "REQUIREMENTS.md" -not -path "*/build/*" | wc -l
   ```
2. Find modules missing COMPLIANCE.md:
   ```bash
   find . -name "pom.xml" -not -path "*/build/*" | xargs grep -l "<artifactId>lego-flow-" | while read f; do
     dir=$(dirname $f);
     if [ ! -f "$dir/doc/COMPLIANCE.md" ] && [ ! -f "$dir/COMPLIANCE.md" ]; then
       echo "$dir"
     fi
   done | sort
   ```

**Expected**: Parent/pom modules don't need COMPLIANCE.md — submodules have their own.

**Verification**: All leaf modules have COMPLIANCE.md. Parent modules are aggregator-only.

## Phase 6: Race Condition Fixes

**Goal**: Eliminate race conditions causing flaky tests.

**Common Patterns**:
1. **Listener before connect** — Always set event listeners before initiating connections:
   ```java
   // BAD: Event may fire before listener is set
   client.connect(ctx);
   server.setListener(listener);
   
   // GOOD: Listener set before connect
   server.setListener(listener);
   client.connect(ctx);
   ```

2. **Selector thread delay** — Add Thread.sleep after server.connect() to allow selector thread initialization:
   ```java
   server.connect(ctx);
   Thread.sleep(50); // Allow selector thread to process channel registration
   ```

3. **CountDownLatch for events** — Use event-driven synchronization:
   ```java
   CountDownLatch latch = new CountDownLatch(1);
   server.setListener(AmqpEventListener.latchOnFirst(latch, AmqpEventListener.EventType.CONNECTION_OPENED));
   client.connect(ctx);
   assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
   ```

**Documentation**: Update module-specific AGENTS.md with patterns discovered.

## Reporting

Update `.hermes/plans/quality2/WIP.md` with:
- Current phase status (Not Started / In Progress / ✅ COMPLETE)
- Metrics table (Total Tests, Passing, Failing, @Disabled, Thread.sleep count, Coverage)
- Progress checklist for each phase
- Recent fixes with commit hashes

## Pitfalls

1. **Thread.sleep in TTL tests is necessary** — Don't try to eliminate Thread.sleep in tests that verify timeout behavior (DnsCacheTest, TokenStoreTest, AuthorizationCodeStoreTest). These tests MUST wait for expiration.

2. **Parent modules don't need COMPLIANCE.md** — Parent/pom modules that aggregate submodules don't require their own COMPLIANCE.md. Submodules have their own.

3. **ACL module has 0% coverage** — This is a known gap. The ACL module has 7 submodules with no tests. Requires architectural decision to add tests.

4. **Interop tests require Docker** — Interop tests are properly disabled or require Docker Compose. Don't try to run them without Docker.

5. **JaCoCo is Maven-only** — Gradle has no jacocoTestReport task. Use Maven `-P jacoco-coverage` for coverage reports.

6. **Gradle project names are `:lego-flow-<module>`** — Not `:messaging:<module>`. Run `./gradlew projects` to discover correct names.

7. **Stale Gradle test XML** — Verify `build/test-results/test/*.xml` mtime is newer than the run start before citing counts. Stale XML makes a test suite that never ran look green.

## References

- `references/coverage-analysis-methodology.md` — Detailed coverage analysis techniques
- `references/thread-sleep-categorization.md` — Thread.sleep analysis decision tree
- `references/disabled-test-documentation.md` — How to document @Disabled tests
