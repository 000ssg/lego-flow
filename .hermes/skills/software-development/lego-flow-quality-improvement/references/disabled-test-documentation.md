# Disabled Test Documentation

## Purpose

All @Disabled tests must have clear, documented reasons. This helps:
1. Future developers understand why the test is disabled
2. Track when tests can be re-enabled
3. Identify systemic issues (e.g., missing features, environment dependencies)

## Documentation Standards

### Required: @Disabled Annotation with Reason
```java
@Disabled("HAVING clause not supported by server")
@Test
void testGroupByWithHaving() throws IOException { ... }
```

### Good Reasons Include:
- **Feature not implemented**: "Server does not support X feature"
- **Environment dependency**: "Requires multicast support on loopback interface"
- **CI limitation**: "Localhost DNS not available in CI"
- **Flaky behavior**: "Timing-dependent with InMemoryMqttTransport — virtual thread race"
- **External dependency**: "Requires external broker (Docker)"

### Bad Reasons (Avoid):
- ~~"Flaky"~~ — Too vague. Explain WHY it's flaky.
- ~~"Broken"~~ — Too vague. Explain WHAT is broken.
- ~~"Temporarily disabled"~~ — When will it be re-enabled?

## Categories of Disabled Tests

### 1. Unsupported Features
**Example**: MySQL AggregateQueryTest
```java
@Disabled("HAVING clause not supported by server")
@Disabled("DISTINCT keyword not supported by server")
@Disabled("COUNT(DISTINCT ...) not supported by server")
@Disabled("Subqueries in SELECT clause not supported by server")
@Disabled("Table aliases (SELECT i.name FROM items AS i) not supported")
```
**Action**: Document in module's doc/COMPLIANCE.md as known limitation.

### 2. Environment-Specific Tests
**Example**: DNS Resolver tests
```java
@Disabled("Localhost DNS not available in CI")
@Disabled("Flaky - depends on system-level socket timeout behavior")
```
**Action**: Keep disabled. These tests require specific environment setup.

### 3. Multicast Dependency
**Example**: DNS-SD Discovery tests
```java
@Disabled("Requires multicast support on the loopback interface")
```
**Action**: Keep disabled. Multicast on loopback is not available in all environments.

### 4. Virtual Thread Race Conditions
**Example**: MQTT tests
```java
@Disabled("MqttClient + InMemoryMqttTransport introduces virtual thread race that blocks")
@Disabled("Timing-dependent with MqttClient + InMemoryMqttTransport — virtual thread race")
@Disabled("Callback threading timing issue — MqttClient listener not triggered before assertion")
```
**Action**: Document in AGENTS.md. May require architectural changes to fix.

### 5. Interop Tests
**Example**: All interop tests
```java
@Disabled("Requires external broker: docker compose -f interop-tests/docker-compose.amqp.yml up -d")
```
**Action**: These are expected. CI runs them with Docker Compose.

## Tracking in WIP.md

Document disabled tests in Phase 3 of WIP.md:

```markdown
## Phase 3: Disabled Test Resolution (P1)

**Status**: ✅ COMPLETE

**Summary**:
- 19 non-interop disabled tests — all properly documented
- 14 interop disabled tests — require external servers (Docker)

### MySQL AggregateQueryTest (6 tests)
```
@Disabled("HAVING clause not supported by server")
@Disabled("DISTINCT keyword not supported by server")
...
```
**Status**: Documented as unsupported features.

### DNS Resolver (4 tests)
```
StubResolverExtendedTest: 3x @Disabled("Localhost DNS not available in CI")
RecursiveResolverTest: 1x @Disabled("Flaky - depends on system-level socket timeout behavior")
```
**Status**: Environment-specific tests.

### DNS-SD Discovery (4 tests)
```
MdnsQuerierTest: 1x @Disabled("Requires multicast support on the loopback interface")
DnsSdBrowserTest: 1x @Disabled("Requires multicast support on the loopback interface")
DnsSdDiscoveryIntegrationTest: 3x @Disabled("Requires multicast support on the loopback interface")
```
**Status**: Requires multicast support on loopback interface.

### MQTT (4 tests)
```
KeepAliveTimeoutTest: 2x @Disabled("Timing-dependent with MqttClient + InMemoryMqttTransport")
MqttAclTopicTest: 1x @Disabled("MqttClient + InMemoryMqttTransport introduces virtual thread race")
InMemoryMqttBridgeTest: 1x @Disabled("Callback threading timing issue")
```
**Status**: Virtual thread race conditions with InMemoryMqttTransport.
```

## Re-Enabling Tests

To re-enable a disabled test:
1. Fix the underlying issue
2. Remove the @Disabled annotation
3. Run the test locally and on CI
4. Update WIP.md to mark as re-enabled
5. Commit with message: `test: re-enable <test-name> — <reason>`

## Audit Command

```bash
# Find all disabled tests (non-interop)
grep -r "@Disabled" --include="*.java" . | grep -v build | grep -v "interop-tests"

# Count by file
grep -r "@Disabled" --include="*.java" . | grep -v build | grep -v "interop-tests" | awk -F: '{print $1}' | sort | uniq -c
```