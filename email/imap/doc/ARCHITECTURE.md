# IMAP Module -- Architecture

This document describes the architectural decisions for the IMAP module.

---

## Protocol Overview

IMAP4rev2 (RFC 9051) is a text-based protocol for accessing email mailboxes on a remote server. Unlike POP3, IMAP supports multiple simultaneous connections, server-side search and sort, fine-grained message access (headers, body sections, partial fetches), persistent flags, and push notifications via IDLE. The Lego Flow implementation provides both server and client over TCP transport.

## Layered Architecture

```mermaid
graph TD
    L1["ImapServer / ImapClient<br/>(TCP connections, configuration, lifecycle)"]
    L2["ImapSession<br/>(connection state machine, command dispatch,<br/>authentication, mailbox selection)"]
    L3["Mailbox Engine<br/>(messages, UIDs, flags, mod-sequences,<br/>subscriptions, expunge)"]
    L4["Search / Sort / Thread<br/>(SearchEngine, SortEngine,<br/>sealed criteria tree, RFC 5256)"]
    L5["Protocol Codec<br/>(ImapCodec, ImapTag, ImapResponse,<br/>literals, sequence sets, quoting)"]
    L6["service module (TCP)<br/>(virtual threads, socket I/O)"]
    L7["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
```

## Connection State Machine

IMAP sessions transition through four states, with each state defining which commands are valid:

```mermaid
stateDiagram-v2
    [*] --> NotAuthenticated : TCP connect + greeting
    NotAuthenticated --> Authenticated : LOGIN / AUTHENTICATE
    Authenticated --> Selected : SELECT / EXAMINE
    Selected --> Authenticated : CLOSE / UNSELECT
    Selected --> Selected : FETCH / STORE / SEARCH / SORT / COPY / MOVE / EXPUNGE
    NotAuthenticated --> [*] : LOGOUT
    Authenticated --> [*] : LOGOUT
    Selected --> [*] : LOGOUT
```

State enforcement is built into the `ImapCommand` enum: each command declares its `ImapState` requirement, and `ImapSession.isCommandAllowed()` checks the current state before dispatch.

## Command Processing Flow

```mermaid
sequenceDiagram
    participant Client
    participant Session as ImapSession
    participant Store as MailStore
    participant Mailbox
    participant Engine as SearchEngine / SortEngine

    Client->>Session: TAG COMMAND args
    Session->>Session: parseCommandLine()
    Session->>Session: isCommandAllowed()
    alt State valid
        Session->>Store: getMailbox() / authenticate() / etc.
        Store-->>Session: result
        Session->>Mailbox: fetch / store / search / etc.
        Mailbox-->>Session: messages / UIDs
        opt SEARCH / SORT
            Session->>Engine: search(mailbox, criteria)
            Engine-->>Session: matching UIDs
        end
        Session->>Client: * untagged data
        Session->>Client: TAG OK completed
    else State invalid
        Session->>Client: TAG BAD not allowed
    end
```

## Server Architecture

```mermaid
graph TD
    TCP["TCP ServerSocket"] --> Accept["Accept Loop<br/>(virtual thread)"]
    Accept --> Session["ImapSession<br/>(one per client,<br/>own virtual thread)"]
    Session --> Store["MailStore<br/>(pluggable backend)"]
    Session --> Idle["IdleNotifier<br/>(push to IDLE sessions)"]
    Store --> MB["Mailbox<br/>(messages, flags, UIDs)"]
    MB --> Index["MessageIndex<br/>(seq-num / UID map)"]
    MB --> Msg["StoredMessage<br/>(content, headers, flags, modSeq)"]
    Session --> FH["FetchHandler<br/>(data item extraction)"]
    Session --> SE["SearchEngine<br/>(criteria evaluation)"]
    Session --> SO["SortEngine<br/>(sort + thread)"]
```

- **ImapServer**: binds TCP socket, runs accept loop on virtual thread executor, creates one `ImapSession` per client connection
- **ImapSession**: reads commands line-by-line, dispatches through `processCommand()` switch expression, manages state transitions
- **MailStore**: interface for storage backends; `InMemoryMailStore` provides thread-safe ConcurrentHashMap-based implementation
- **Mailbox**: contains messages keyed by UID, tracks mod-sequences for CONDSTORE, manages permanent/session flags
- **MessageIndex**: CopyOnWriteArrayList maintaining UID ordering for seq-num/UID bidirectional mapping
- **IdleNotifier**: ConcurrentHashMap of mailbox-name to listener lists; sessions register during IDLE, receive push notifications for EXISTS/EXPUNGE/FLAG changes

## Client Architecture

```mermaid
graph TD
    App["Application Code"] --> Client["ImapClient<br/>(high-level API)"]
    Client --> Conn["ImapConnection<br/>(TCP socket, tag gen,<br/>command/response I/O)"]
    Client --> FV["FolderView<br/>(selected mailbox state)"]
    Client --> IM["IdleManager<br/>(IDLE loop on<br/>virtual thread)"]
    Conn --> Tag["ImapTag<br/>(A001, A002, ...)"]
    Conn --> Codec["ImapCodec<br/>(encode/parse)"]
```

- **ImapClient**: wraps `ImapConnection`, provides typed methods for all IMAP operations, parses responses into domain objects (`FolderView`, `FetchResult`, search results)
- **ImapConnection**: manages Socket lifecycle, sends commands via `PrintWriter`, reads responses line-by-line via `BufferedReader`, tracks capabilities
- **ImapClientConfig**: fluent builder with host, port, credentials, TLS toggle, connect/read/idle timeouts
- **FolderView**: captures SELECT/EXAMINE response metadata (message count, UID validity/next, flags, highest mod-seq)
- **FetchResult**: parses `* N FETCH (...)` response lines into key-value data items
- **IdleManager**: runs IDLE/DONE loop on virtual thread, delivers untagged responses to a notification callback, re-issues IDLE after configurable timeout

## Search Criteria Architecture

The `SearchCriteria` sealed interface uses algebraic data types for type-safe, exhaustive search expression trees:

```mermaid
graph TD
    SC["SearchCriteria<br/>(sealed interface)"] --> Leaf["Leaf Criteria"]
    SC --> Comp["Composite Criteria"]
    Leaf --> All & Flagged & Header & AddressField & Body & Text & Subject
    Leaf --> Before & On & Since & SentBefore & SentOn & SentSince
    Leaf --> Larger & Smaller & Keyword & Unkeyword & Uid & SequenceSet & ModSeq
    Comp --> And & Or & Not
```

- Each variant is a record implementing `toWire()` for protocol serialization
- `SearchEngine.matches()` uses pattern matching switch on all sealed subtypes
- Factory methods on the interface provide fluent construction: `SearchCriteria.from("alice").and(SearchCriteria.unseen())`

## CONDSTORE Extension

```mermaid
sequenceDiagram
    participant Client
    participant Server
    Client->>Server: STORE 1 (UNCHANGEDSINCE 5) +FLAGS (\Seen)
    alt msg.modSeq <= 5
        Server->>Server: storeFlags(), increment modSeq
        Server->>Client: * 1 FETCH (FLAGS (\Seen) MODSEQ (6))
        Server->>Client: TAG OK STORE completed
    else msg.modSeq > 5
        Server->>Client: TAG OK [MODIFIED 1] conditional failed
    end
```

- `ModSequence`: atomic counter per mailbox, incremented on every flag/message change
- `ConditionalStore.conditionalStore()`: compares message modSeq against UNCHANGEDSINCE threshold before applying flags
- `ConditionalStore.findModified()`: returns messages changed since a given mod-seq (used for QRESYNC-style sync)

## Thread Safety Model

| Component | Concurrency Strategy |
|-----------|---------------------|
| `ImapServer` | `AtomicBoolean` for running state, virtual thread executor |
| `ImapSession` | one session per thread, `volatile` state fields, `AtomicBoolean` running |
| `MailStore` / `InMemoryMailStore` | `ConcurrentHashMap` for mailboxes and users |
| `Mailbox` | `ConcurrentHashMap` for messages, `AtomicLong` for UID/modSeq counters |
| `StoredMessage` | `CopyOnWriteArraySet` for flags, `volatile` modSeq |
| `MessageIndex` | `CopyOnWriteArrayList` for UID list |
| `IdleNotifier` | `ConcurrentHashMap` + `CopyOnWriteArrayList` for listeners |
| `ImapTag` | `AtomicInteger` counter |

## Integration with Lego Flow

| Lego Flow Module | Usage in IMAP |
|------------------|---------------|
| `blocks` | DP<I,O> for message processing pipeline, DF<T> for filtering, Statistics for metrics |
| `service` | TCP socket connections, virtual thread pools for server accept loop and client sessions |
| `email-common` | Shared MIME parsing for body structure analysis and content type handling |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
