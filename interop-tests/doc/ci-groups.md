# CI Parallel Test Groups

## Strategy

Tests are split into **5 parallel groups** that share no Docker services, enabling maximum parallelism.
Each group is identified by a `-Dgroups` Maven filter or `@Tags` annotation.

## Group Definitions

### Group 1: `web-protocols` (fast, lightweight)
- HTTP against nginx — `HttpNginxInteropTest` (4 tests)
- DNS against BIND/coredns — `DnsInteropTest` (8 tests)
- Redis — `RedisInteropTest` (8 tests)
- **Total: 20 tests**
- **Containers: nginx, redis**

### Group 2: `database-protocols`
- PostgreSQL — `PostgresqlInteropTest` (7 tests)
- LDAP — `LdapInteropTest` (7 tests)
- **Total: 14 tests**
- **Containers: postgresql, openldap**

### Group 3: `email-protocols`
- SMTP — `SmtpInteropTest` (8 tests)
- FTP — `FtpInteropTest` (10 tests)
- SSH — `SshServerInteropTest` (8 tests)
- **Total: 26 tests**
- **Containers: mailhog, ftp, sshd**

### Group 4: `messaging-protocols`
- MQTT — `MqttMosquittoInteropTest` (5 tests)
- NATS — `NatsInteropTest` (8 tests)
- STOMP — `StompInteropTest` (7 tests)
- AMQP 0-9-1 — `Amqp091InteropTest` (21 tests)
- AMQP 1.0 — `AmqpInteropTest` (7 tests, disabled)
- XMPP — `XmppInteropTest` (6 tests)
- **Total: 54 tests (47 active)**
- **Containers: mosquitto, nats, activemq, rabbitmq, prosody**

### Group 5: `terminal-protocols` (heavy, longest-running)
- Telnet client — `TelnetClientInteropTest` (7 tests)
- Telnet server — `TelnetServerInteropTest` (24 tests)
- Terminal emulators — `TerminalEmulatorInteropTest` (25 tests)
- TN3270/TN5250 — `TN3270TN5250InteropTest` (69 tests)
- **Total: 125 tests**
- **Containers: telnetd**

## Execution Commands

```bash
# Run all groups sequentially (CI default)
mvn verify -pl interop-tests -am -DskipInteropTests=false

# Run specific groups in parallel (GitHub Actions matrix)
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=web-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=database-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=email-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=messaging-protocols
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dgroups=terminal-protocols
```

## Parallel Execution Note

The current interop tests do not use `@Tag` annotations for Maven group filtering.
To enable parallel CI execution, each test class should be annotated:

```java
@Tag("web-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpNginxInteropTest { ... }
```

## Service Isolation Rules

| Group          | Shared Services     | Conflicts          |
|---------------|--------------------|--------------------|
| web-protocols  | nginx, redis       | None               |
| database-protocols | postgresql, openldap | None           |
| email-protocols | mailhog, ftp, sshd | None             |
| messaging-protocols | mosquitto, nats, activemq, rabbitmq, prosody | None |
| terminal-protocols | telnetd       | None               |

All groups are **100% isolated** — no service overlaps.
