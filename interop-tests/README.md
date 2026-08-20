# Lego Flow Interoperability Tests

Integration tests that connect Lego Flow protocol implementations to real reference
server implementations for protocol compliance validation.

## Services

| Service      | Image                         | Port  | Purpose                              |
|------------- |-------------------------------|-------|--------------------------------------|
| nginx        | `nginx:alpine`                | 8080  | HTTP/HTTPS reference server          |
| mosquitto    | `eclipse-mosquitto:latest`    | 1883  | MQTT broker reference implementation |
| redis        | `redis:7-alpine`             | 6379  | Redis in-memory store                |
| postgresql   | `postgres:17-alpine`         | 5432  | PostgreSQL database server           |
| rabbitmq     | `rabbitmq:4-management`      | 5672  | AMQP 1.0 broker                      |
| nats         | `nats:latest`                 | 4222  | NATS message broker                  |
| prosody      | `prosody/prosody:latest`      | 5222  | XMPP server                          |
| activemq     | `apache/activemq:latest`      | 61613 | STOMP broker                         |
| openldap     | `osixia/openldap:latest`      | 389   | LDAP v3 server                       |

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

# Start all 4 service containers (nginx, mosquitto, redis, postgresql):
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
mvn verify -pl interop-tests -am -P all -DskipInteropTests=false
# Expected: 45+ tests, 0 failures
```

#### Gradle

```bash
cd /path/to/lego-flow
./gradlew :interop-tests:test -DskipInteropTests=false --console=plain
# Expected: 45+ tests, 0 failures
```

#### Verify Results

Check the JUnit XML reports after execution:

```bash
# Maven: tail interop-tests/target/surefire-reports/*.txt
# Gradle: cat interop-tests/build/reports/tests/test/index.html
# Both should show: "Tests run: 21, Failures: 0"
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

## Test Coverage

### HTTP (nginx) — `HttpNginxInteropTest`
- Health endpoint returns 200 OK
- JSON API endpoint returns expected payload
- Echo endpoint reflects HTTP method
- HTML homepage serves valid content

### MQTT (Mosquitto) — `MqttMosquittoInteropTest`
- Connect and disconnect lifecycle
- Publish and subscribe message delivery
- Wildcard topic subscriptions (`sensor/+/kitchen`)
- Multi-topic subscription routing

### Redis — `RedisInteropTest`
- PING/PONG roundtrip
- SET/GET string operations
- INCR atomic increment
- HSET/HGET/HGETALL hash operations
- RPUSH/LPOP list operations
- KEYS pattern matching
- TTL expiry verification

### PostgreSQL — `PostgresqlInteropTest`
- Version query compatibility
- CREATE TABLE + INSERT + SELECT roundtrip
- Aggregate functions (COUNT, SUM, GROUP BY)
- Transaction support (BEGIN/Rollback isolation)
- Connection parameter validation (user, database, host, port)

## CI Integration

Interoperability tests run against Docker Compose containers in GitHub Actions CI.
Services are started with `docker compose -f interop-tests/docker-compose.yml up -d`,
health-checked, and stopped after the tests complete (even on failure).
