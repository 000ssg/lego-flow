
# Lego Flow NATS Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-343-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

NATS protocol module for the Lego Flow framework, providing cloud-native messaging with core pub/sub and JetStream persistent streaming.

## Overview

This module implements the NATS text protocol (core) and JetStream extensions, enabling Java applications to build NATS servers and clients for microservice communication, event streaming, and request/reply patterns. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
NATS Client / Server (headless core, transport-injected)
  -> JetStream (streams, consumers, pull subscriptions, ack policies)
    -> Message Router (subscription matching, queue groups, echo suppression)
      -> Subject Engine (subject model, wildcard matching *, >)
        -> Protocol Codec (text-based encode/decode for all 12 operations)
          -> NatsTransport SPI (InMemory for tests/demos, Pipeline for real TCP)
            -> Service Layer (NatsService / NatsServerService + SelectableChannelManager)
```

## Features

- **Core NATS protocol** -- text-based protocol with 12 operations (INFO, CONNECT, PUB, HPUB, SUB, UNSUB, MSG, HMSG, PING, PONG, +OK, -ERR)
- **Publish/Subscribe** -- subject-based message routing with decoupled producers and consumers
- **Subject wildcards** -- single-token (`*`) and multi-level (`>`) subscription filters
- **Queue groups** -- round-robin load balancing among subscribers sharing a queue group name
- **Request/Reply** -- automatic inbox management with CompletableFuture-based timeout handling
- **Headers (NATS/1.0)** -- HPUB/HMSG with multi-value headers, status codes, and case-insensitive lookup
- **Server** -- headless core (connections via `handleConnection`), client registry, message routing, authentication; real TCP owned by the service layer
- **Client** -- transport-injected connect, publish, subscribe, request/reply, auto-unsubscribe, reader loop on virtual thread
- **Transport SPI** -- `NatsTransport` byte-level SPI: `InMemoryNatsTransport` (tests/demos), `PipelineNatsTransport` (production, selector-thread driven)
- **Service layer** -- `NatsService` / `NatsServerService` drive real TCP through `SelectableChannelManager`; zero raw sockets in the protocol core
- **Authentication** -- pluggable Authenticator interface with token-based and user/password implementations
- **JetStream** -- persistent streaming with streams, consumers, pull subscriptions, and acknowledgement
- **Stream configuration** -- retention policies (limits/interest/workqueue), discard policies (old/new), max messages/bytes/age
- **Consumer configuration** -- deliver policies (all/last/new/by_start_seq), ack policies (none/all/explicit), max ack pending
- **Sealed protocol model** -- `ParsedOp` sealed interface with 12 record variants for type-safe dispatch

## Quick Start

### Start a server (service layer, real TCP)

```java
var ctx = new DefaultServiceContext(ServiceUser.anonymous());
var manager = new SelectableChannelManager(ctx);
manager.startEventLoop();

var server = NatsServerService.builder().port(4222).build();
server.connect(ctx);   // non-blocking listener owned by the manager
```

### Connect a client and subscribe

```java
var service = NatsService.builder("localhost", 4222).build();
service.connect(ctx);
var client = service.getClient();

client.subscribe("orders.>", msg ->
    System.out.println("Order: " + msg.dataAsString()));
```

### Publish a message

```java
client.publish("orders.new", "order-123".getBytes());
```

### Request/Reply

```java
// Service
client.subscribe("math.add", msg -> {
    int result = /* compute */;
    client.publish(msg.replyTo(), String.valueOf(result).getBytes());
});

// Requester
var reply = client.request("math.add", "2+3", Duration.ofSeconds(5));
System.out.println("Result: " + reply.dataAsString());
```

### Queue group load balancing

```java
client1.subscribe("tasks", "workers", msg -> process(msg));
client2.subscribe("tasks", "workers", msg -> process(msg));
// Each message delivered to only one worker (round-robin)
```

### JetStream persistent streaming

```java
var jsManager = server.jetStreamManager();
var streamConfig = StreamConfig.builder("ORDERS")
    .subjects("orders.>")
    .retention(StreamConfig.RetentionPolicy.LIMITS)
    .maxMsgs(10000)
    .build();
var stream = jsManager.createStream(streamConfig);

var consumerConfig = ConsumerConfig.builder()
    .durable("order-processor")
    .deliverPolicy(ConsumerConfig.DeliverPolicy.ALL)
    .ackPolicy(AckPolicy.EXPLICIT)
    .build();
var consumer = jsManager.createConsumer("ORDERS", consumerConfig);
var pullSub = jsManager.pullSubscribe("ORDERS", "order-processor");

var messages = pullSub.fetch(10);
for (var msg : messages) {
    process(msg);
    pullSub.ack(msg);
}
```

### Authentication

```java
var server = NatsServerService.builder().port(4222).build();
var auth = new UserPassAuthenticator()
    .addUser("admin", "secret");
server.getServer().setAuthenticator(auth);
server.connect(ctx);

var clientService = NatsService.builder("localhost", 4222).build();
clientService.connect(ctx);
var client = clientService.getClient();
```

## Package Structure

```
ssg.legoflow.messaging.nats/
├── protocol/          -- Protocol codec: 12 operations, ConnectOptions, ServerInfo, NatsHeaders, NatsStatus
├── client/            -- Client: connect, pub/sub, request/reply, inbox management, subscriptions (transport-injected)
├── server/            -- Server: headless core, client connections, message routing, queue groups
│   └── auth/          -- Authentication: Authenticator interface, token, user/pass
├── subject/           -- Subject engine: Subject model, SubjectMatcher (wildcards), SubscriptionRegistry
├── transport/         -- NatsTransport SPI: InMemoryNatsTransport (tests/demos), PipelineNatsTransport (production)
├── service/           -- NatsService / NatsServerService: the only layer that owns (manager-managed) channels
├── jetstream/         -- JetStream: streams, consumers, pull subscriptions, ack policies, stream store
└── demo/              -- Demo applications and examples
```

## Demo Applications

1. **PubSubDemo** -- Basic publish/subscribe messaging with subject patterns
2. **RequestReplyDemo** -- Service pattern with request/reply and timeout handling
3. **QueueGroupDemo** -- Queue group load balancing across multiple subscribers
4. **JetStreamDemo** -- Persistent streaming with streams, consumers, and pull subscriptions

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
