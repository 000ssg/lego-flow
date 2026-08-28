# AMQP DP/DF/Service Refinement Plan

**Branch:** cleanup-1 | **Created:** 2026-08-27 | **Status:** IN PROGRESS

## Execution State

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1: Restore Protocol Layer | IN PROGRESS | AmqpClient/AmqpContainer/FrameCodec are protocol impl, not deprecated |
| Phase 2: Service Wrappers | NOT STARTED | AmqpClientService → AmqpClient, AmqpContainerService → AmqpContainer |
| Phase 3: Debug Utilities Cleanup | COMPLETED | Deleted capture/, CapturingTransport, TrafficCapture |
| Phase 4: Async Interop Tests | NOT STARTED | Service.async() + CountDownLatch, all 3 brokers |
| Phase 5: Coverage Tests (80%+) | NOT STARTED | Unit tests for protocol classes |
| Phase 6: Documentation | NOT STARTED | Guidelines, AGENTS.md, module docs |
| Phase 7: Final Build & Commit | NOT STARTED | Maven + Gradle + commit |

## Architecture

```
AmqpClientService (sync service)
  └── AmqpClient (blocking protocol — SASL, OPEN, sessions, links)
        └── AmqpTransport (TcpTransport or InMemoryTransport)

AmqpClientService.async() → DefaultAsyncService (virtual threads)
  └── CompletableFutures for all operations

AmqpContainerService (sync service)
  └── AmqpContainer (blocking server — accept, SASL, connections)
        └── AmqpTransport (TcpTransport or InMemoryTransport)
```

### DECISION-1: Protocol logic lives in blocking classes
Following Redis, MQTT, SSH pattern: the blocking client IS the protocol layer.
The service is a thin wrapper. `.async()` provides the async API via virtual threads.
AmqpClient/AmqpContainer/FrameCodec are NOT deprecated — they follow the established pattern.

### DECISION-2: Qpid uses QPID_DISPATCH mode (no SASL)
Qpid Dispatch Router uses proto-0 (AMQP_HEADER first, no SASL).
BrokerMode.QPID_DISPATCH configures this correctly. Tests use this mode for Qpid.

### DECISION-3: Only debug utilities are truly deprecated
capture/, CapturingTransport, TrafficCapture were debug/trafﬁc capture tools
never used by production code or tests. These were already deleted.

---

## Phase 1: Restore Protocol Layer

Restore from git: AmqpClient, AmqpContainer, TcpTransport, FrameCodec
These are the protocol implementation — same pattern as RedisClient in RedisClientService.

---

## Phase 2: Service Wrappers

Rewrite AmqpClientService to delegate to AmqpClient:
```java
class AmqpClientService extends AbstractService<ByteBuffer, ByteBuffer> {
    private volatile AmqpClient client;
    protected void doConnect(ServiceContext ctx) {
        client = new AmqpClient(config);
        client.connect();  // blocking on virtual thread
    }
    public AmqpClient getClient() { return client; }
}
```

Rewrite AmqpContainerService to delegate to AmqpContainer:
```java
class AmqpContainerService extends AbstractService<ByteBuffer, ByteBuffer> {
    private volatile AmqpContainer container;
    protected void doConnect(ServiceContext ctx) {
        container = new AmqpContainer(config);
        container.start();
    }
}
```

---

## Phase 3: Debug Utilities Cleanup (DONE)

Deleted: capture/, CapturingTransport, TrafficCapture

---

## Phase 4: Async Interop Tests

Rewrite BrokerInteropTest using async API:
```java
AmqpClientService service = AmqpClientService.builder(host, port).build();
var ctx = DefaultServiceContext.builder(ServiceUser.anonymous()).build();
service.async().connect(ctx).get(10, SECONDS);
AmqpClient client = service.getClient();
AmqpSession session = client.createSession();
// ... latch-based receive ...
```

Broker configs:
| Broker | Port | Mode | Auth |
|--------|------|------|------|
| RabbitMQ | 5672 | RABBITMQ | ANONYMOUS |
| QpidDispatch | 5674 | QPID_DISPATCH | ANONYMOUS (no SASL) |
| Artemis | 5675 | ARTEMIS | PLAIN (admin/admin) |

---

## Phase 5: Coverage Tests (Target: 80%+)

Unit tests for all protocol classes using InMemoryTransport.

---

## Phase 6: Documentation

Update AGENTS.md, README.md, doc/ARCHITECTURE.md, doc/REQUIREMENTS.md.

---

## Phase 7: Final Build & Commit

1. `mvn clean test -pl '!benchmarks'` — ALL modules
2. `./gradlew clean test --rerun-tasks` — ALL modules
3. Verify all interop tests pass against 3 brokers
4. Commit with detailed message
5. Update REQUIREMENTS.md with final metrics
