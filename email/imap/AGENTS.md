# email / imap — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `imap` module implements IMAP4rev2 (RFC 9051) for email mailbox access. It provides both server and client implementations with extensions for IDLE (RFC 2177), CONDSTORE (RFC 7162), NAMESPACE (RFC 2342), SORT/THREAD (RFC 5256), MOVE (RFC 6851), and LIST-EXTENDED (RFC 5258). Built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `ImapServer` -- TCP server accepting connections, dispatching to per-client sessions via virtual threads
- `ImapSession` -- per-client session managing connection state, command dispatch, and mailbox operations
- `ImapClient` -- high-level client with login, select, fetch, store, search, sort, copy, move, IDLE
- `ImapConnection` -- low-level protocol I/O: tag generation, command sending, response collection
- `MailStore` -- pluggable mail storage backend interface (authentication, mailbox CRUD, listing)
- `ImapCodec` -- text-based codec for IMAP wire format (commands, responses, literals, sequences)
- `SearchEngine` -- evaluates SEARCH criteria against stored messages
- `SortEngine` -- server-side SORT and THREAD (ORDEREDSUBJECT, REFERENCES) per RFC 5256
- `FetchHandler` -- processes FETCH data items (FLAGS, ENVELOPE, BODY sections, partials)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | IMAP wire format: commands with state validation, responses (tagged/untagged/continuation), status codes (OK/NO/BAD/BYE/PREAUTH), codec, tags, literals, fetch data items, search criteria (sealed interface with 20+ record variants), sort criteria, thread algorithms |
| `server` | Server implementation: `ImapServer` (TCP listener, virtual threads), `ImapSession` (full command set), `MailStore`/`InMemoryMailStore` (storage backend), `Mailbox` (messages, flags, UIDs, mod-sequences), `StoredMessage` (content, headers, flags), `MessageIndex` (seq-num/UID mapping), `FetchHandler`, `SearchEngine`, `SortEngine`, `IdleNotifier` (push notifications), `NamespaceConfig` |
| `client` | Client implementation: `ImapClient` (high-level API), `ImapConnection` (TCP lifecycle, capability negotiation), `ImapClientConfig` (builder with timeouts, TLS), `FolderView` (selected mailbox state), `FetchResult` (parsed FETCH response), `IdleManager` (IDLE loop on virtual thread) |
| `condstore` | CONDSTORE extension (RFC 7162): `ConditionalStore` (UNCHANGEDSINCE flag updates, MODSEQ search), `ModSequence` (atomic mod-seq tracker) |
| `demo` | `ImapDemo`: end-to-end workflow (server setup, seed messages, client login, list, select, fetch, flag, copy, expunge, logout) |

## IMAP-Specific Coding Conventions

### Connection States (RFC 9051 Section 3)
- **Not Authenticated** -- LOGIN, AUTHENTICATE, STARTTLS allowed
- **Authenticated** -- SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE, UNSUBSCRIBE, LIST, NAMESPACE, STATUS, APPEND, IDLE allowed
- **Selected** -- FETCH, STORE, COPY, MOVE, SEARCH, SORT, THREAD, EXPUNGE, CLOSE, UNSELECT, UID allowed
- **Any State** -- CAPABILITY, NOOP, LOGOUT always allowed

### Command Tag Protocol
- Client sends: `TAG COMMAND args\r\n` (e.g., `A001 LOGIN user pass`)
- Server responds with untagged `* data` lines, then tagged `TAG STATUS text` (e.g., `A001 OK LOGIN completed`)
- Tags are generated with prefix + zero-padded counter: A001, A002, A003...
- Special tags: `*` (untagged), `+` (continuation)

### Search Criteria (Sealed Interface)
- `SearchCriteria` is a sealed interface with 20+ record implementations
- Leaf criteria: `All`, `Flagged`, `Header`, `AddressField`, `Body`, `Text`, `Subject`, `Before`/`On`/`Since`, `SentBefore`/`SentOn`/`SentSince`, `Larger`/`Smaller`, `Keyword`/`Unkeyword`, `Uid`, `SequenceSet`, `ModSeq`
- Composite criteria: `And`, `Or`, `Not`
- Pattern matching in `SearchEngine.matches()` uses exhaustive switch on sealed types

### UID vs Sequence Number Addressing
- Sequence numbers are 1-based, dense, change on expunge
- UIDs are unique, monotonically increasing, persist across sessions
- `UID FETCH`/`UID STORE`/`UID SEARCH`/`UID COPY`/`UID MOVE` use UID addressing
- `MessageIndex` maintains bidirectional seq-num/UID mapping

### Capabilities Advertised
```
IMAP4rev2 IDLE NAMESPACE CONDSTORE SORT THREAD=ORDEREDSUBJECT
THREAD=REFERENCES MOVE LIST-EXTENDED LITERAL+ UNSELECT
```

## Testing Practices

- Unit tests for protocol layer: command parsing, tag generation, state validation
- `ImapCommandTest`: parse all commands case-insensitive, unknown command rejection, state requirements per command category
- `ImapTagTest`: default/custom prefix, sequential generation, reset, counter inspection, constant values
- All tests use JUnit 5 + AssertJ
- Test count: 17
