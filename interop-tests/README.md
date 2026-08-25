# Lego Flow Interoperability Tests

Integration tests that connect Lego Flow protocol implementations to real reference
server implementations for protocol compliance validation.

## Documentation

- [**Compatibility Report**](doc/COMPATIBILITY.md) — Cross-checking of Lego Flow implementations against reference implementations, with quality assessment
- [**CI Parallel Groups**](doc/ci-groups.md) — Test grouping strategy for parallel CI execution

## Services

| Service      | Image                         | Port  | Purpose                              |
|------------- |-------------------------------|-------|--------------------------------------|
| nginx        | `nginx:alpine`                | 8080  | HTTP/HTTPS reference server          |
| mosquitto    | `eclipse-mosquitto:latest`    | 1883  | MQTT broker reference implementation |
| redis        | `redis:7-alpine`             | 6379  | Redis in-memory store                |
| postgresql   | `postgres:17-alpine`         | 5432  | PostgreSQL database server           |
| rabbitmq     | `rabbitmq:4-management`      | 5672  | AMQP 1.0 broker (via amqp1.0 plugin) |
| activemq     | `apache/activemq:latest`      | 61613 | STOMP broker                         |
| nats         | `nats:2.10-alpine`            | 4222  | NATS message broker                  |
| prosody      | `prosody/prosody:latest`      | 5222  | XMPP server                          |
| openldap     | `osixia/openldap:latest`      | 389   | LDAP v3 server                       |
| mailhog      | `mailhog/mailhog:latest`      | 25    | SMTP server                          |
| ftp          | `docker/ftp-python`           | 21    | FTP server                           |
| sshd         | `docker/sshd`                 | 2222  | SSH server                           |
| telnetd      | `docker/telnetd`              | 2223  | Telnet server                        |

## Prerequisites

- **Docker Engine** installed and running (20.10+ recommended)
- **Docker Compose plugin** installed (`docker compose --version` should work; note: this is the newer `docker compose`, not the legacy `docker-compose` binary)
- Docker daemon accessible without `sudo` (user in docker group on Linux, or Docker Desktop running on macOS/Windows)

## Quick Start

### 1. Start the reference services

```bash
# Verify Docker is running:
docker ps
# Expected: no errors

# Start all service containers:
cd interop-tests
docker compose up -d

# Wait a few seconds for health checks to pass:
docker compose ps
# All services should show "healthy" in STATUS column

# Verify connectivity (optional but recommended):
curl http://localhost:8080/health
redis-cli -p 6379 ping
```

### 2. Run interoperability tests

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
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=web-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=database-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=email-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=messaging-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=terminal-protocols
```

### 3. Stop the services

```bash
docker compose -f interop-tests/docker-compose.yml down
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
Tests are split into 5 groups for parallel execution — see [ci-groups.md](doc/ci-groups.md) for details.

Services are started with `docker compose -f interop-tests/docker-compose.yml up -d`,
health-checked, and stopped after the tests complete (even on failure).

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
