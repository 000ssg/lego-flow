# CI Parallel Test Groups

## Strategy

Tests are split into **4 parallel groups**, each identified by a JUnit `@Tag`
and run in its own CI job (`.github/workflows/ci.yml`, `interoperability-tests`
matrix). Each job starts **only the Docker services its protocols need**.

A group is selected with `-Dinterop.group=<group>` — `interop-tests/pom.xml`
maps it onto the surefire `<groups>` filter. When the property is set, an
`interop-group` profile turns on `failIfNoTests` (a tag typo that matches
zero tests fails the build instead of passing silently); pass
`-Dinterop.failIfNoTests=false` to override (used for `interop-kafka` /
`interop-wamp` until the Phase 6 composite tests land).

## Group Definitions

### Group 1: `interop-messaging-core`
- MQTT — `MqttMosquittoInteropTest` (3), `MqttV5FeatureTest` (3)
- STOMP — `StompInteropTest` (3)
- AMQP 1.0 — `AmqpInteropTest` (6)
- AMQP 1.0 wire capture (reference clients) — `Amqp10WireCaptureTest` (3, Artemis CLI),
  `AmqpWireCaptureTest` (1, aiormq)
- **Total: 22 tests**
- **Containers: mosquitto, rabbitmq (STOMP 61613), artemis**

### Group 2: `interop-kafka`
- Kafka — composite tests (Phase 6: connect + ApiVersions + metadata → produce/fetch →
  multi-partition streaming → transactions)
- **Total: tests land in Phase 6**
- **Containers: kafka (confluentinc/cp-kafka, single-node KRaft)**

### Group 3: `interop-wamp`
- WAMP — composite tests (Phase 6: HELLO/WELCOME + pub/sub → multi-realm →
  distributed procedure executors → sharding)
- **Total: tests land in Phase 6**
- **Containers: crossbar (crossbario/crossbar, WAMP router; host 8081)**

### Group 4: `interop-rest` — CI job disabled for now
- NATS, XMPP, DNS, FTP, HTTP, LDAP, PostgreSQL, Redis, SMTP, SSH, Telnet,
  Terminal (all **existing** tests, re-tagged)
- **186 tests** — CI job disabled until proper rest interop is implemented
  (deferred per user direction). The protocol→group composition of this group
  is a **frozen decision** — Phase 5 only builds the infrastructure (tag, pom
  selection, compose services) around it; the eventual re-shuffle of protocols
  across groups is a separate decision.

## Execution Commands

```bash
# Run all interop tests (all groups)
mvn verify -pl interop-tests -am -DskipInteropTests=false

# Run a single group
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-messaging-core
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-kafka -Dinterop.failIfNoTests=false
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-wamp -Dinterop.failIfNoTests=false
```

## Service Isolation

| Group                  | Containers                              | Conflicts |
|------------------------|------------------------------------------|-----------|
| interop-messaging-core | mosquitto, rabbitmq, artemis              | None      |
| interop-kafka          | kafka                                     | None      |
| interop-wamp           | crossbar                                  | None      |
| interop-rest (disabled)| (existing reference services)             | None      |

All active groups are **100% isolated** — each CI job starts only its own
containers. Crossbar maps container 8080 → host 8081 so it can share a host
with nginx (8080).
