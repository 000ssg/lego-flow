
# Lego Flow SMTP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-201-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

SMTP protocol module for the Lego Flow framework, providing complete client and server implementations for email message transfer.

## Overview

This module implements the Simple Mail Transfer Protocol (RFC 5321) with ESMTP extensions, enabling Java applications to send and receive email over SMTP. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
SmtpClient / SmtpServer (application layer)
  -> SASL Authentication (PLAIN, LOGIN, CRAM-MD5, XOAUTH2)
    -> ESMTP Extensions (SIZE, 8BITMIME, STARTTLS, PIPELINING, CHUNKING, DSN)
      -> Protocol Codec (command/reply text encoding, dot-stuffing)
        -> TCP Transport (service module, virtual threads)
```

## Features

- **RFC 5321 SMTP** -- complete client and server implementations
- **ESMTP extensions** -- SIZE, 8BITMIME, STARTTLS, AUTH, PIPELINING, CHUNKING, DSN, ENHANCEDSTATUSCODES, SMTPUTF8
- **SASL authentication** -- PLAIN (RFC 4616), LOGIN, CRAM-MD5 (RFC 2195), XOAUTH2
- **TLS support** -- STARTTLS upgrade (RFC 3207) and implicit TLS
- **Chunked transfer** -- BDAT command (RFC 3030) for large/binary messages
- **Enhanced status codes** -- RFC 3463 / RFC 2034 structured error reporting (X.Y.Z format)
- **Delivery Status Notifications** -- RFC 3461/3464 bounce, delay, and success reports
- **Relay control** -- domain-based restrictions, sender allow/block lists, auth requirement
- **Pluggable message store** -- `MessageStore` interface with in-memory implementation
- **Pluggable handler** -- `SmtpHandler` interface for sender/recipient/message acceptance policies
- **Virtual threads** -- one virtual thread per client connection on the server side
- **JDK-only** -- no external dependencies beyond SLF4J for logging

## Quick Start

### Send a simple email

```java
var config = SmtpClientConfig.builder("smtp.example.com", 587)
    .tlsMode(SmtpClientConfig.TlsMode.STARTTLS)
    .auth("user", "password")
    .build();
try (var client = new SmtpClient(config)) {
    client.connect();
    client.send("sender@example.com",
        List.of("recipient@example.com"),
        "Subject: Test\r\n\r\nHello!");
}
```

### One-shot message submission

```java
var result = MessageSubmission.sendSimple(
    SmtpClientConfig.builder("smtp.example.com", 587)
        .tlsMode(SmtpClientConfig.TlsMode.STARTTLS)
        .auth("user", "password")
        .build(),
    "sender@example.com",
    List.of("recipient@example.com"),
    "Hello Subject",
    "Hello, this is the message body.");
if (!result.success()) {
    System.err.println("Failed: " + result.message());
}
```

### Start an SMTP server

```java
var store = new InMemoryMessageStore();
try (var server = new SmtpServer("localhost", 2525)) {
    server.setMessageStore(store);
    server.setHandler(SmtpHandler.acceptAll());
    server.start();
    // server is now accepting connections on port 2525
}
```

### Authenticated server with relay restrictions

```java
var handler = new SmtpHandler() {
    @Override
    public boolean authenticate(String username, String password) {
        return "user".equals(username) && "pass".equals(password);
    }
    @Override
    public boolean acceptRecipient(String recipient, String sender) {
        return recipient.endsWith("@example.com");
    }
};
var relayConfig = RelayConfig.builder()
    .allowDomain("example.com")
    .requireAuth(true)
    .maxMessageSize(5 * 1024 * 1024)
    .build();

try (var server = new SmtpServer("localhost", 2525)) {
    server.setMessageStore(new InMemoryMessageStore());
    server.setHandler(handler);
    server.setRelayConfig(relayConfig);
    server.start();
}
```

## Package Structure

```
ssg.legoflow.email.smtp/
├── protocol/          -- SMTP command/reply codec, ESMTP extensions, dot-stuffing, enhanced status codes
├── auth/              -- SASL authentication: PLAIN, LOGIN, CRAM-MD5, XOAUTH2
├── server/            -- SMTP server: session state machine, relay config, message store, handler
├── client/            -- SMTP client: connection management, DATA/BDAT transfer, MessageSubmission
├── dsn/               -- Delivery Status Notifications: report model and MIME generator
└── demo/              -- Demo applications and examples
```

## Demo Applications

1. **SimpleSmtpDemo** -- Local server and client exchanging a single plain text message
2. **MultiRecipientDemo** -- Sending a message to multiple recipients in one transaction
3. **AuthSmtpDemo** -- Server with authentication requirement and domain-based relay restrictions

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads
- `lego-flow-email-common` -- Shared MIME parsing (RFC 2045-2049)

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
