# CI Parallel Test Groups

## Strategy

Tests are split into **4 parallel groups**, each identified by a JUnit `@Tag`
and run in its own CI job (`.github/workflows/ci.yml`, `interoperability-tests`
matrix). Each job starts **only the reference services its group owns** — and
it does so from its **own compose file**, so groups never share (or intersect)
Docker instances:

| Group | Compose file | Services |
|-------|--------------|----------|
| `interop-messaging-core` | `docker-compose.core.yml`  | artemis, rabbitmq, mosquitto |
| `interop-kafka`          | `docker-compose.kafka.yml` | kafka |
| `interop-wamp`           | `docker-compose.wamp.yml`  | crossbar |
| `interop-rest` (disabled)| *(own file when enabled)*  | nginx, redis, postgres, nats, xmpp, openldap, smtp, ftp, sshd, telnet, dns |

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
- **Total: 19 tests**
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

Each group's services come from its own compose file:

```bash
cd interop-tests
docker compose -f docker-compose.core.yml up -d   # + ps / down
docker compose -f docker-compose.kafka.yml up -d  # + ps / down
docker compose -f docker-compose.wamp.yml up -d   # + ps / down
```

## Service Isolation

Isolation is at the **compose-file level**: each active group has a dedicated
file containing only that group's services, so `up -d <file>` can never start
another group's instances, and two groups can run on the same host without
port conflicts.

| Group                  | Compose file               | Containers                                  | Conflicts |
|------------------------|----------------------------|---------------------------------------------|-----------|
| interop-messaging-core | docker-compose.core.yml    | artemis-test, rabbitmq-test, mosquitto-test | None      |
| interop-kafka          | docker-compose.kafka.yml   | kafka-test                                  | None      |
| interop-wamp           | docker-compose.wamp.yml    | wamp-router-test                            | None      |
| interop-rest (disabled)| (own file when enabled)    | (existing reference services)               | None      |

Crossbar maps container 8080 → host 8081 so the wamp group can share a host
with nginx (8080) when the rest group is enabled.
