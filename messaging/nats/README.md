
# Lego Flow NATS Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-271-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

NATS protocol module for the Lego Flow framework, providing cloud-native messaging with core pub/sub and JetStream persistent streaming.

## Overview

This module implements the NATS text protocol (core) and JetStream extensions, enabling Java applications to build NATS servers and clients for microservice communication, event streaming, and request/reply patterns. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
NATS Client / Server (application layer)
  -> JetStream (streams, consumers, pull subscriptions, ack policies)
    -> Message Router (subscription matching, queue groups, echo suppression)
      -> Subject Engine (subject model, wildcard matching *, >)
        -> Protocol Codec (text-based encode/decode for all 12 operations)
          -> TCP Transport (service module channels)
```

## Features

- **Core NATS protocol** -- text-based protocol with 12 operations (INFO, CONNECT, PUB, HPUB, SUB, UNSUB, MSG, HMSG, PING, PONG, +OK, -ERR)
- **Publish/Subscribe** -- subject-based message routing with decoupled producers and consumers
- **Subject wildcards** -- single-token (`*`) and multi-level (`>`) subscription filters
- **Queue groups** -- round-robin load balancing among subscribers sharing a queue group name
- **Request/Reply** -- automatic inbox management with CompletableFuture-based timeout handling
- **Headers (NATS/1.0)** -- HPUB/HMSG with multi-value headers, status codes, and case-insensitive lookup
- **Server** -- TCP listener with virtual threads, client registry, message routing, authentication
- **Client** -- connect, publish, subscribe, request/reply, auto-unsubscribe, reader loop on virtual thread
- **Authentication** -- pluggable Authenticator interface with token-based and user/password implementations
- **JetStream** -- persistent streaming with streams, consumers, pull subscriptions, and acknowledgement
- **Stream configuration** -- retention policies (limits/interest/workqueue), discard policies (old/new), max messages/bytes/age
- **Consumer configuration** -- deliver policies (all/last/new/by_start_seq), ack policies (none/all/explicit), max ack pending
- **Sealed protocol model** -- `ParsedOp` sealed interface with 12 record variants for type-safe dispatch

## Quick Start

### Start a server

```java
var server = new NatsServer(4222);
server.start();
```

### Connect a client and subscribe

```java
var client = new NatsClient("localhost", 4222);
client.connect();
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
var server = new NatsServer(4222);
var auth = new UserPassAuthenticator()
    .addUser("admin", "secret");
server.setAuthenticator(auth);
server.start();

var client = new NatsClient("localhost", 4222,
    ConnectOptions.withDefaults("my-client").withUserPass("admin", "secret"));
client.connect();
```

## Package Structure

```
ssg.legoflow.messaging.nats/
├── protocol/          -- Protocol codec: 12 operations, ConnectOptions, ServerInfo, NatsHeaders, NatsStatus
├── client/            -- Client: connect, pub/sub, request/reply, inbox management, subscriptions
├── server/            -- Server: TCP accept, client connections, message routing, queue groups
│   └── auth/          -- Authentication: Authenticator interface, token, user/pass
├── subject/           -- Subject engine: Subject model, SubjectMatcher (wildcards), SubscriptionRegistry
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
