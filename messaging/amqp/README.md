
# Lego Flow AMQP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-264-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

AMQP 1.0 protocol module for the Lego Flow framework, providing container (server) and client implementations for advanced message queuing.

## Overview

This module implements AMQP 1.0 (ISO 19464 / OASIS), enabling Java applications to build message containers and clients for enterprise messaging. The architecture is transport-agnostic, layering protocol handling on top of the framework's service and blocks abstractions:

```
AMQP Container / Client (connection management, API surface)
  -> Session Multiplexing (incoming/outgoing windows, transfer-id tracking)
    -> Link Layer (credit-based flow control, sender/receiver attach/detach)
      -> Delivery Management (delivery-id/tag, settlement, outcomes)
        -> Performative Codec (9 performatives as described lists)
          -> Type System Codec (22 primitive + composite types, self-describing)
            -> Frame Codec (8-byte header + body encoding)
              -> Transport SPI (PipelineTransport: selector + blocking / in-memory)
                -> service module (SelectableChannelManager, ServiceContext)
```

- **Service layer**: `AmqpClientService` and `AmqpContainerService` wire the protocol stack through `SelectableChannelManager` (server) or virtual threads (client)

## Features

- **AMQP 1.0 (ISO 19464)** -- full protocol stack from type system to connection management
- **Container (server)** -- accepts connections, manages sessions/links, routes messages by address
- **Client** -- connect, create sessions, attach sender/receiver links, send/receive messages
- **9 performatives** -- Open, Begin, Attach, Flow, Transfer, Disposition, Detach, End, Close
- **Credit-based flow control** -- link-level credit grants, session-level incoming/outgoing windows
- **Delivery semantics** -- at-most-once (pre-settled), at-least-once (settled on accept), exactly-once
- **6 delivery states** -- Received, Accepted, Rejected, Released, Modified, TransactionalState
- **Complete type system** -- 22 AMQP primitive types + list, map, array, described types
- **SASL authentication** -- ANONYMOUS, PLAIN, EXTERNAL mechanisms with pluggable authenticator
- **Message model** -- all 7 sections: header, delivery/message annotations, properties, application properties, body (data/value/sequence), footer
- **Transport-agnostic core** -- AmqpTransport SPI with TCP and in-memory implementations
- **Virtual threads** -- container uses virtual thread executor for high-concurrency connection handling

## Quick Start

### Start a container

```java
var config = ContainerConfig.defaults();
var container = new AmqpContainer(config);
container.start();
```

### Connect a client and send a message

```java
var config = ClientConfig.builder()
    .host("localhost").port(5672)
    .containerId("my-app")
    .build();
try (var client = new AmqpClient(config)) {
    client.connect();
    AmqpSession session = client.createSession();
    SenderLink sender = client.createSender(session, "sender-1", "my-queue");

    client.send(sender, AmqpMessage.of("Hello AMQP!"), true);
}
```

### Receive messages

```java
try (var client = new AmqpClient(config)) {
    client.connect();
    AmqpSession session = client.createSession();
    ReceiverLink receiver = client.createReceiver(session, "receiver-1", "my-queue");

    Delivery delivery = receiver.receive(5, TimeUnit.SECONDS);
    System.out.println("Received: " + delivery.message().bodyAsString());
    receiver.accept(delivery.deliveryId());
}
```

### Request/reply with message properties

```java
var request = new AmqpMessage()
    .properties(Properties.builder()
        .messageId("req-1")
        .correlationId(UUID.randomUUID().toString())
        .replyTo("reply-queue")
        .build())
    .bodyString("What is the answer?");

client.send(requestSender, request, true);
```

### SASL PLAIN authentication

```java
var config = ClientConfig.builder()
    .host("localhost").port(5672)
    .saslMechanism(new PlainMechanism("user", "password"))
    .build();
```

## Package Structure

```
ssg.legoflow.messaging.amqp/
|-- client/            -- Client: connect, session, sender/receiver links, SASL
|-- client/service/    -- AmqpClientService (DP/DF-based service wrapper)
|-- container/         -- Container (server): accept, route, session/link management
|-- container/service/ -- AmqpContainerService (DP/DF-based service wrapper)
|-- common/            -- Constants, error conditions, connection state, exceptions
|-- delivery/          -- Delivery tracking, delivery states (sealed), codec
|-- link/              -- Sender and receiver links with credit-based flow control
|-- message/           -- Message model (7 sections), message codec, header, properties
|-- sasl/              -- SASL mechanisms (ANONYMOUS, PLAIN, EXTERNAL), authenticator
|-- session/           -- Session multiplexing, flow control windows, link registry
|-- transport/         -- Frame codec, performative codec, transport SPI, PipelineTransport
|-- types/             -- AMQP type system (22 types), binary codec, descriptors
+-- demo/              -- Demo applications and examples
```

## Demo Applications

1. **SimpleSendReceiveDemo** -- Container + producer + consumer, basic message flow
2. **PubSubDemo** -- One publisher, multiple subscribers on the same address
3. **RequestReplyDemo** -- Request/reply pattern using correlation-id and reply-to properties
4. **TransactionDemo** -- Transactional delivery states (commit/rollback)

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
