# Secondary Broker Interop Strategy

## Golden Rule
**If code works against the primary reference broker, ONLY modify test code for secondary brokers.**

The primary reference (e.g. Artemis for AMQP 1.0) is the source of truth. If your implementation
passes the primary reference, never touch core protocol code (client, container, codec, link
classes) to fix secondary broker interop failures. Changes to "work" with a secondary broker
will almost certainly break the primary.

## What to modify for secondary brokers

- **Test configuration** — broker mode enum, addressing format, credentials, SASL mechanism
- **Docker provisioning** — plugin enablement, queue pre-creation, configuration injection
- **Test assertions** — allow broker-specific timing, field defaults, error codes
- **docker-compose.yml** — container config, entrypoint scripts, volume mounts

## What NOT to modify

- Protocol client classes (`AmqpClient`, `MqttClient`, etc.)
- Frame codecs and type encoders
- Link/session management logic
- Connection handshake ordering (unless proven wrong against primary reference)
- Flow control and credit management

## Real example: AMQP 1.0 RabbitMQ interop

**Problem:** RabbitMQ 4.x AMQP 1.0 plugin requires pre-created queues and `/queues/` addressing.
Client works perfectly against Artemis.

**Correct fix:**
- Updated `docker-compose.yml` to pre-create `interop-test-queue` via `rabbitmqadmin`
- Updated `AmqpInteropTest.java` to detect `RABBITMQ` broker mode and use correct address format
- Zero changes to `AmqpClient.java`, `ReceiverLink.java`, or `PerformativeCodec.java`

**Incorrect fix (what NOT to do):**
- Changing `ReceiverLink.issueCredit()` to pass different Flow frame fields
- Rewriting handshake logic in `AmqpClient.connect()`
- Modifying type encoding in `TypeCodec.java`

## Session evidence

During the cleanup-1 session, the agent was corrected for modifying core AMQP code
(`ReceiverLink.issueCredit()`, `AmqpClient.connect()`) to fix RabbitMQ failures. The
original code worked against Artemis — the user explicitly stated: *"The lego-flow AMQP 1.0
version in git worked fine with Artemis. It needs only LIMITED corrections in tests for
RabbitMQ, no original code changes!"*

The agent reverted all core changes and fixed the issue by:
1. Pre-creating the queue in RabbitMQ via `rabbitmqadmin` in docker entrypoint
2. Using `/queues/` address prefix in the interop test for RabbitMQ broker mode
