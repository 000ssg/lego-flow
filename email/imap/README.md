
# Lego Flow IMAP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-17-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

IMAP protocol module for the Lego Flow framework, providing IMAP4rev2 (RFC 9051) server and client implementations for email mailbox access.

## Overview

This module implements the IMAP4rev2 protocol with extensions for IDLE, CONDSTORE, NAMESPACE, SORT/THREAD, MOVE, and LIST-EXTENDED. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
IMAP Client / Server (application layer)
  -> Session Management (connection states, authentication, mailbox selection)
    -> Mailbox Engine (messages, UIDs, flags, mod-sequences, subscriptions)
      -> Search / Sort / Thread (RFC 5256, flag/date/size/text criteria)
        -> Protocol Codec (text-based commands, responses, literals, sequences)
          -> TCP Transport (service module, virtual threads)
```

## Features

- **IMAP4rev2 (RFC 9051)** -- full command set across all four connection states
- **Server** -- virtual-thread-per-client architecture, pluggable mail store, IDLE push notifications
- **Client** -- high-level API for login, mailbox operations, fetch, search, IDLE management
- **Protocol codec** -- tagged commands/responses, untagged data, continuation responses, literal strings
- **Search** -- sealed criteria tree with AND/OR/NOT composition, flag/header/date/size/body matching
- **SORT/THREAD (RFC 5256)** -- server-side sort by date/from/to/subject/size, threading by ORDEREDSUBJECT and REFERENCES
- **IDLE (RFC 2177)** -- push notifications for new messages, flag changes, expunges
- **CONDSTORE (RFC 7162)** -- modification sequences, UNCHANGEDSINCE conditional flag updates
- **NAMESPACE (RFC 2342)** -- personal/other-users/shared namespace configuration
- **MOVE (RFC 6851)** -- atomic move between mailboxes
- **LIST-EXTENDED (RFC 5258)** -- extended LIST with subscription status
- **LITERAL+ (RFC 7888)** -- non-synchronizing literals
- **UNSELECT (RFC 3691)** -- unselect without expunge

## Quick Start

### Start a server

```java
var store = new InMemoryMailStore();
store.addUser("user", "pass");

var server = new ImapServer("127.0.0.1", 143, store);
server.start();
```

### Connect a client

```java
var config = ImapClientConfig.builder("127.0.0.1", 143)
    .credentials("user", "pass")
    .connectTimeout(Duration.ofSeconds(10))
    .build();

try (var client = new ImapClient(config)) {
    client.connect();
    client.login();

    // List mailboxes
    List<String> mailboxes = client.list("", "*");

    // Select INBOX
    FolderView folder = client.select("INBOX");

    // Fetch messages
    List<FetchResult> messages = client.fetch("1:*", "(FLAGS ENVELOPE)");

    // Search for unseen
    List<Integer> unseen = client.search("UNSEEN");

    // Flag and expunge
    client.store("1", "+FLAGS", "(\\Seen)");
    client.store("3", "+FLAGS", "(\\Deleted)");
    client.expunge();
}
```

### IDLE push notifications

```java
var idleManager = new IdleManager(client.connection(),
    notification -> System.out.println("Update: " + notification),
    Duration.ofMinutes(25).toMillis());
idleManager.start();
// ... notifications arrive as mailbox changes occur ...
idleManager.stop();
```

## Package Structure

```
ssg.legoflow.email.imap/
├── protocol/          -- Wire format: commands, responses, codec, tags, literals,
│                         fetch/search/sort criteria
├── server/            -- Server: ImapServer, ImapSession, MailStore, Mailbox,
│                         MessageIndex, FetchHandler, SearchEngine, SortEngine,
│                         IdleNotifier, NamespaceConfig
├── client/            -- Client: ImapClient, ImapConnection, ImapClientConfig,
│                         FolderView, FetchResult, IdleManager
├── condstore/         -- CONDSTORE extension: ConditionalStore, ModSequence
└── demo/              -- ImapDemo: end-to-end workflow example
```

## Demo Application

**ImapDemo** -- Starts an in-memory IMAP server, seeds it with test messages, then connects a client to perform a complete workflow: login, list mailboxes, select INBOX, fetch messages, search unseen, flag as seen, copy to Sent, delete and expunge, logout.

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads
- `lego-flow-email-common` -- Shared MIME parsing (RFC 2045-2049)

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
