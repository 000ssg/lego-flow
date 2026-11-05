# Lego Flow Interoperability Tests

Integration tests that connect Lego Flow protocol implementations to real reference
server implementations for protocol compliance validation.

## Documentation

- [**Compatibility Report**](doc/COMPATIBILITY.md) — Cross-checking of Lego Flow implementations against reference implementations, with quality assessment
- [**CI Parallel Groups**](doc/ci-groups.md) — Test grouping strategy for parallel CI execution

## Services

Reference services are split into **per-group compose files** — each interop
group owns exactly the services its protocols need, so CI jobs (and local runs)
start in isolation and never mix instances across groups.

| Compose file                 | Group                     | Services                                    |
|------------------------------|---------------------------|---------------------------------------------|
| `docker-compose.core.yml`    | `interop-messaging-core`  | artemis (5675, 8161), rabbitmq (5672, 61613, 15672), mosquitto (1883) |
| `docker-compose.kafka.yml`   | `interop-kafka`           | kafka (9092)                                |
| `docker-compose.wamp.yml`    | `interop-wamp`            | crossbar (8081)                             |

The `interop-rest` group's services (nginx, redis, postgres, nats, xmpp,
openldap, smtp, ftp, sshd, telnet, dns) are provisioned separately when that
group is enabled — they are deliberately **not** in the three active files
above. See [ci-groups.md](doc/ci-groups.md).

## Prerequisites

- **Docker Engine** installed and running (20.10+ recommended)
- **Docker Compose plugin** installed (`docker compose --version` should work; note: this is the newer `docker compose`, not the legacy `docker-compose` binary)
- Docker daemon accessible without `sudo` (user in docker group on Linux, or Docker Desktop running on macOS/Windows)

## Quick Start

Each group has its own compose file — start only the group you are running:

```bash
cd interop-tests

# core group (MQTT, STOMP, AMQP 1.0)
docker compose -f docker-compose.core.yml up -d
# kafka group
docker compose -f docker-compose.kafka.yml up -d
# wamp group
docker compose -f docker-compose.wamp.yml up -d
```

Wait for the health checks to pass (each file's own `ps` shows `healthy`):

```bash
docker compose -f docker-compose.core.yml ps
```

### Run interoperability tests

#### Maven

```bash
cd /path/to/lego-flow
mvn verify -pl interop-tests -am -DskipInteropTests=false
# Expected: 216 tests, 0 failures
```

#### Gradle

```bash
cd /path/to/lego-flow
./gradlew :interop-tests:test -DskipInteropTests=false --console=plain
# Expected: 216 tests, 0 failures
```

#### Run Specific CI Groups (Parallel Execution)

Each group uses isolated Docker containers and can run in parallel:

```bash
# Individual groups
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-messaging-core
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-kafka -Dinterop.failIfNoTests=false
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dinterop.group=interop-wamp -Dinterop.failIfNoTests=false
```

`interop-messaging-core` = MQTT + STOMP + AMQP (15 tests). `interop-kafka` /
`interop-wamp` carry the Phase 6 composite tests (group service is provisioned
and healthy; `-Dinterop.failIfNoTests=false` until the tests land).
`interop-rest` = all remaining existing interop tests — CI-disabled for now
(frozens composition; see [ci-groups.md](doc/ci-groups.md)).

### Stop the services

```bash
cd interop-tests
docker compose -f docker-compose.core.yml down
docker compose -f docker-compose.kafka.yml down
docker compose -f docker-compose.wamp.yml down
```

## Custom Configuration

Override the default host/port via system properties:

```bash
mvn test -pl interop-tests \
  -Dinterop.nginx.host=custom-host \
  -Dinterop.nginx.port=9090 \
  -Dinterop.mosquitto.host=mqtt.local \
  -Dinterop.redis.host=redis.local \
  -Dinterop.pg.host=pg.local \
  -Dinterop.pg.user=postgres_user \
  -Dinterop.pg.password=secret
```

## Test Coverage by Protocol

| Protocol | Test Class | Tests | Reference | Direction |
|----------|-----------|-------|-----------|-----------|
| HTTP | `HttpNginxInteropTest` | 4 | nginx | Client → Server |
| DNS | `DnsInteropTest` | 8 | BIND | Client → Server |
| Redis | `RedisInteropTest` | 8 | Redis 7 | Client → Server |
| PostgreSQL | `PostgresqlInteropTest` | 7 | PostgreSQL 17 | Client → Server |
| LDAP | `LdapInteropTest` | 7 | OpenLDAP | Client → Server |
| SMTP | `SmtpInteropTest` | 8 | MailHog | Client → Server |
| FTP | `FtpInteropTest` | 10 | pyftpdlib | Client → Server |
| SSH | `SshServerInteropTest` | 8 | OpenSSH | Client → Server |
| MQTT | `MqttMosquittoInteropTest` | 5 | Mosquitto | Client → Server |
| NATS | `NatsInteropTest` | 8 | NATS 2.10 | Client → Server |
| STOMP | `StompInteropTest` | 7 | ActiveMQ | Client → Server |
| AMQP 1.0 | `AmqpInteropTest` | 7 | RabbitMQ 4 | Client → Server |
| XMPP | `XmppInteropTest` | 6 | Prosody | Client → Server |
| Telnet Client | `TelnetClientInteropTest` | 7 | telnetd | Client → Server |
| Telnet Server | `TelnetServerInteropTest` | 24 | telnetd | Server → Client |
| Terminal Emulators | `TerminalEmulatorInteropTest` | 25 | VT100/XTERM | Rendering QA |
| TN3270/TN5250 | `TN3270TN5250InteropTest` | 69 | 3270/5250 emu | Rendering QA |

**Total: 17 test classes, ~195 tests**

### Dual Implementation Testing

Protocols with both Lego Flow client and server implementations are tested in **both directions**:

- **SSH**: `SshServerInteropTest` tests version exchange with OpenSSH and with our SSH client
- **Telnet**: `TelnetClientInteropTest` (client against telnetd) + `TelnetServerInteropTest` (server against telnetd client)

## CI Integration

Interoperability tests run against Docker Compose containers in GitHub Actions CI.
Tests are split into 4 groups for parallel execution — see [ci-groups.md](doc/ci-groups.md)
for details:

| Group | CI job | Services |
|-------|--------|----------|
| `interop-messaging-core` | ✅ active | mosquitto, rabbitmq, artemis |
| `interop-kafka` | ✅ active (failIfNoTests=false) | kafka |
| `interop-wamp` | ✅ active (failIfNoTests=false) | crossbar |
| `interop-rest` | ❌ disabled for now | (existing services) |

Each CI job starts **only its group's** containers, health-checks them, and
stops them after the tests complete (even on failure).

## Test Results & Quality Assessment

The [**Compatibility Report**](doc/COMPATIBILITY.md) provides a detailed assessment of Lego Flow implementation quality:

- **Overall quality: 72%**
- Core protocol handshake and basic data exchange: **90% complete**
- Security features (TLS, auth): **40% complete**
- Advanced features (streaming, transactions): **50% complete**
- Edge cases and error handling: **60% complete**

## Next Steps

1. Add TLS/SSL support for SMTP, SSH, and XMPP
2. Add file transfer (STOR/RETR) for FTP
3. Add advanced authentication for LDAP (filter predicates)
4. Add streaming (GET/POST with streaming body) for HTTP
5. Add auth and TLS for SMTP
6. Add key exchange and authentication for SSH
