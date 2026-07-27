# IMAP Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 17
- **Dependencies**: blocks (DP/DF), service (TCP transport), email-common (MIME parsing)
- **Standards**: IMAP4rev2 (RFC 9051), IDLE (RFC 2177), CONDSTORE (RFC 7162), NAMESPACE (RFC 2342), SORT/THREAD (RFC 5256), MOVE (RFC 6851), LIST-EXTENDED (RFC 5258), LITERAL+ (RFC 7888), UNSELECT (RFC 3691)

---

## Requirements

### Protocol Codec
1. Encode tagged commands for the wire: `TAG COMMAND args\r\n`
2. Parse command lines into tag, command name, and argument components
3. Handle IMAP quoting rules: atoms, quoted strings with escape sequences, NIL
4. Encode and parse literal strings with synchronizing `{N}\r\n` and non-synchronizing `{N+}\r\n` headers
5. Parse parenthesized lists with nested grouping and quoted string handling
6. Parse sequence sets with ranges (`1:5`), individual numbers, wildcards (`*`), and comma separation
7. Format and parse flag lists `(\Seen \Flagged \Deleted)`
8. Parse IMAP response lines: tagged (with status and optional response code), untagged (`*`), continuation (`+`)

### Command Set (27 commands across 4 states)
1. **Any State**: CAPABILITY, NOOP, LOGOUT
2. **Not Authenticated**: LOGIN, AUTHENTICATE, STARTTLS
3. **Authenticated**: SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE, UNSUBSCRIBE, LIST, NAMESPACE, STATUS, APPEND, IDLE
4. **Selected**: FETCH, STORE, COPY, MOVE, SEARCH, SORT, THREAD, EXPUNGE, CLOSE, UNSELECT, UID
5. Each command declares its required connection state for server-side enforcement
6. UID prefix mode for FETCH, STORE, SEARCH, COPY, MOVE commands

### IMAP Server
1. Accept TCP connections and send greeting with capabilities
2. Dispatch each client to its own virtual thread via `ImapSession`
3. Process commands line-by-line with state validation before dispatch
4. Transition between connection states as commands execute
5. Support ephemeral port binding (port 0) for testing
6. Graceful shutdown with socket close and executor shutdown

### Mail Store
1. Pluggable storage backend via `MailStore` interface
2. `InMemoryMailStore`: thread-safe, ConcurrentHashMap-based, auto-creates INBOX per user
3. Authenticate users with username/password
4. CRUD operations for mailboxes (create, delete, rename, get, list)
5. Wildcard mailbox listing with `*` (any) and `%` (non-delimiter) patterns
6. Hierarchy delimiter configuration (default `/`)
7. INBOX cannot be deleted or renamed per RFC

### Mailbox Operations
1. Append messages with content, flags, and internal date; assign monotonically increasing UIDs
2. Retrieve messages by UID or 1-based sequence number
3. Store flags with SET, ADD, REMOVE operations and optional .SILENT modifier
4. Copy messages between mailboxes preserving content and flags
5. Move messages atomically (copy + delete from source)
6. Expunge messages marked with `\Deleted` flag
7. Track permanent flags (`\Seen`, `\Answered`, `\Flagged`, `\Deleted`, `\Draft`, `\*`)
8. Maintain UIDVALIDITY per mailbox
9. Report EXISTS, RECENT, UNSEEN counts and first unseen sequence number

### FETCH Command
1. Support all standard data items: FLAGS, INTERNALDATE, RFC822.SIZE, ENVELOPE, BODYSTRUCTURE, BODY, UID
2. Support BODY[section] and BODY.PEEK[section] with HEADER, TEXT, HEADER.FIELDS, HEADER.FIELDS.NOT, MIME part numbers
3. Support partial fetches with `<offset.length>` syntax
4. Expand fetch macros: ALL, FAST, FULL
5. BODY.PEEK does not set `\Seen` flag; BODY[] does (when mailbox is read-write)
6. Format ENVELOPE from message headers (Date, Subject, From, Sender, Reply-To, To, Cc, Bcc, In-Reply-To, Message-ID)
7. Format BODYSTRUCTURE with content type, charset, encoding, size, line count

### SEARCH Command
1. Sealed `SearchCriteria` interface with 20+ record implementations
2. Flag criteria: SEEN, UNSEEN, ANSWERED, UNANSWERED, FLAGGED, UNFLAGGED, DELETED, UNDELETED, DRAFT, UNDRAFT, NEW, OLD, RECENT
3. Address/header criteria: FROM, TO, CC, BCC, SUBJECT, HEADER field, BODY text, TEXT
4. Date criteria: BEFORE, ON, SINCE (internal date), SENTBEFORE, SENTON, SENTSINCE (Date header)
5. Size criteria: LARGER, SMALLER
6. Keyword criteria: KEYWORD, UNKEYWORD
7. Identifier criteria: UID set, sequence set
8. CONDSTORE criteria: MODSEQ
9. Composite criteria: AND (implicit juxtaposition), OR, NOT
10. Return sequence numbers or UIDs depending on UID mode

### SORT and THREAD (RFC 5256)
1. Sort keys: ARRIVAL (internal date), CC, DATE, FROM, SIZE, SUBJECT, TO
2. REVERSE modifier for descending sort order
3. Combined sort criteria with tie-breaking priority
4. Subject base stripping: remove Re:, Fw:, Fwd:, mailing list tags `[...]`
5. THREAD ORDEREDSUBJECT: group by base subject, sort by date within groups
6. THREAD REFERENCES: thread by References and In-Reply-To headers with parent-child tree construction

### IDLE (RFC 2177)
1. Server sends `+ idling` continuation, waits for `DONE`
2. Sessions register for mailbox notifications during IDLE
3. Push untagged responses for EXISTS (new messages), EXPUNGE, FLAG changes
4. Client `IdleManager` runs IDLE/DONE loop on virtual thread with configurable timeout

### CONDSTORE (RFC 7162)
1. Monotonically increasing modification sequence per mailbox
2. Every flag change and message append increments mod-seq and associates with affected message
3. `UNCHANGEDSINCE` precondition for conditional STORE
4. `MODSEQ` search criterion for finding messages changed since a given value
5. Report `HIGHESTMODSEQ` in SELECT/EXAMINE response

### NAMESPACE (RFC 2342)
1. Personal, other users, and shared namespace entries with prefix and delimiter
2. Default configuration: single personal namespace with empty prefix
3. Wire format: `(("" "/")) NIL NIL` for default

### Client API
1. Fluent `ImapClientConfig` builder: host, port, credentials, TLS, connect/read/idle timeouts
2. `ImapConnection`: TCP lifecycle, tag generation, command send/collect, capability tracking
3. `ImapClient`: typed methods for all IMAP operations returning domain objects
4. `FolderView`: selected mailbox metadata (message count, UID validity/next, flags, highest mod-seq, read-only status)
5. `FetchResult`: parse `* N FETCH (...)` into key-value data item map
6. `IdleManager`: virtual-thread-based IDLE loop with notification callback

### Demo Application
1. ImapDemo: start in-memory server, seed INBOX with 3 messages, create Sent/Drafts/Trash folders
2. Client workflow: connect, login, list, select, fetch, search unseen, flag, copy, delete, expunge, logout

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
