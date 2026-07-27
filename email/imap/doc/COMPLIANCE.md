# IMAP Compliance Report

## Specifications Covered
- IMAP4rev2 -- RFC 9051 (August 2021)
- IDLE -- RFC 2177 (June 1997)
- NAMESPACE -- RFC 2342 (May 1998)
- CONDSTORE -- RFC 7162 (March 2014)
- SORT/THREAD -- RFC 5256 (June 2008)
- MOVE -- RFC 6851 (January 2013)
- LIST-EXTENDED -- RFC 5258 (June 2008)
- LITERAL+ -- RFC 7888 (May 2016)
- UNSELECT -- RFC 3691 (February 2004)

## Compliance Matrix

### RFC 9051 -- Connection States

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3.1 | Not Authenticated state | ✅ Implemented | `ImapSession` starts in `NOT_AUTHENTICATED`; `ImapCommand.ImapState` |
| 3.2 | Authenticated state | ✅ Implemented | Transitions on successful LOGIN; `ImapCommandTest.testAuthenticatedCommands` |
| 3.3 | Selected state | ✅ Implemented | Transitions on SELECT/EXAMINE; `ImapCommandTest.testSelectedCommands` |
| 3.4 | Logout state | ✅ Implemented | `ImapSession.handleLogout()` sends BYE + OK |
| 3 | State-based command validation | ✅ Implemented | `ImapSession.isCommandAllowed()` checks `ImapCommand.requiredState()` |

### RFC 9051 -- Any-State Commands

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 6.1.1 | CAPABILITY command | ✅ Implemented | `ImapSession.handleCapability()`; returns all supported extensions |
| 6.1.2 | NOOP command | ✅ Implemented | `ImapSession.handleNoop()` |
| 6.1.3 | LOGOUT command | ✅ Implemented | `ImapSession.handleLogout()`; sends `* BYE` then tagged OK |

### RFC 9051 -- Not Authenticated State Commands

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 6.2.2 | LOGIN command | ✅ Implemented | `ImapSession.handleLogin()`; delegates to `MailStore.authenticate()` |
| 6.2.1 | AUTHENTICATE command | ⚠️ Partial | `ImapSession.handleAuthenticate()`; PLAIN mechanism stub only |
| 6.2.3 | STARTTLS command | ❌ Not implemented | Returns BAD; TLS via implicit TLS (port 993) not yet supported |

### RFC 9051 -- Authenticated State Commands

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 6.3.1 | SELECT command | ✅ Implemented | `ImapSession.handleSelect()`; returns EXISTS, RECENT, UIDVALIDITY, UIDNEXT, FLAGS, PERMANENTFLAGS, UNSEEN, HIGHESTMODSEQ |
| 6.3.2 | EXAMINE command | ✅ Implemented | `ImapSession.handleSelect(readOnly=true)`; opens READ-ONLY |
| 6.3.3 | CREATE command | ✅ Implemented | `ImapSession.handleCreate()`; delegates to `MailStore.createMailbox()` |
| 6.3.4 | DELETE command | ✅ Implemented | `ImapSession.handleDelete()`; INBOX deletion blocked per RFC |
| 6.3.5 | RENAME command | ✅ Implemented | `ImapSession.handleRename()`; INBOX rename blocked per RFC |
| 6.3.6 | SUBSCRIBE command | ✅ Implemented | `ImapSession.handleSubscribe()`; per-user subscription tracking |
| 6.3.7 | UNSUBSCRIBE command | ✅ Implemented | `ImapSession.handleUnsubscribe()` |
| 6.3.8 | LIST command | ✅ Implemented | `ImapSession.handleList()`; wildcard matching, hierarchy delimiter, subscription attributes |
| 6.3.10 | STATUS command | ✅ Implemented | `ImapSession.handleStatus()`; MESSAGES, RECENT, UIDNEXT, UIDVALIDITY, UNSEEN, HIGHESTMODSEQ |
| 6.3.11 | APPEND command | ✅ Implemented | `ImapSession.handleAppend()`; literal reading, flags, APPENDUID response code |
| 2.2.1 | Server greeting | ✅ Implemented | `ImapSession.run()` sends `* OK [CAPABILITY ...] IMAP4rev2 server ready` |

### RFC 9051 -- Selected State Commands

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 6.4.5 | FETCH command | ✅ Implemented | `ImapSession.handleFetch()`; `FetchHandler` processes all data items |
| 6.4.5 | FETCH data items: FLAGS | ✅ Implemented | `FetchHandler.fetchItem()` |
| 6.4.5 | FETCH data items: INTERNALDATE | ✅ Implemented | `FetchHandler.fetchItem()` |
| 6.4.5 | FETCH data items: RFC822.SIZE | ✅ Implemented | `FetchHandler.fetchItem()` |
| 6.4.5 | FETCH data items: ENVELOPE | ✅ Implemented | `FetchHandler.formatEnvelope()` |
| 6.4.5 | FETCH data items: BODYSTRUCTURE | ✅ Implemented | `FetchHandler.formatBodyStructure(extended=true)` |
| 6.4.5 | FETCH data items: BODY (non-extension) | ✅ Implemented | `FetchHandler.formatBodyStructure(extended=false)` |
| 6.4.5 | FETCH data items: BODY[section] | ✅ Implemented | `FetchHandler.fetchBodySection()`; HEADER, TEXT, HEADER.FIELDS, MIME parts |
| 6.4.5 | FETCH data items: BODY.PEEK[section] | ✅ Implemented | Does not set \Seen flag |
| 6.4.5 | FETCH partial `<offset.length>` | ✅ Implemented | `FetchDataItem.partial()` |
| 6.4.5 | FETCH macros: ALL, FAST, FULL | ✅ Implemented | `ImapSession.expandFetchMacro()` |
| 6.4.5 | FETCH data items: UID | ✅ Implemented | Auto-added in UID FETCH mode |
| 6.4.6 | STORE command | ✅ Implemented | `ImapSession.handleStore()`; FLAGS, +FLAGS, -FLAGS with .SILENT |
| 6.4.7 | COPY command | ✅ Implemented | `ImapSession.handleCopy()`; preserves flags and content |
| 6.4.4 | SEARCH command | ✅ Implemented | `ImapSession.handleSearch()`; `SearchEngine` evaluates criteria |
| 6.4.8 | EXPUNGE command | ✅ Implemented | `ImapSession.handleExpunge()`; sends `* N EXPUNGE` for each |
| 6.4.2 | CLOSE command | ✅ Implemented | Expunges then transitions to Authenticated |
| 6.4.1 | UID prefix command | ✅ Implemented | `ImapSession.processLine()` handles UID FETCH/STORE/SEARCH/COPY/MOVE |

### RFC 9051 -- Response Format

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 7.1 | Tagged responses (OK, NO, BAD) | ✅ Implemented | `ImapResponse.tagged()`; `ImapStatus` enum |
| 7.1 | Untagged responses (`*`) | ✅ Implemented | `ImapResponse.untagged()` |
| 7.5 | Continuation responses (`+`) | ✅ Implemented | `ImapResponse.continuation()`; used for IDLE and APPEND |
| 7.1 | Response codes `[...]` | ✅ Implemented | `ImapResponse` parses and exposes `responseCode()` |
| 7.1 | Status responses: OK, NO, BAD, BYE, PREAUTH | ✅ Implemented | `ImapStatus` enum with 5 values |
| 2.6 | Literal strings `{N}\r\n` | ✅ Implemented | `ImapLiteral`; synchronizing and non-synchronizing (`{N+}`) |
| 2.3.1 | Sequence sets `1:5,7,10:*` | ✅ Implemented | `ImapCodec.parseSequenceSet()` |

### RFC 2177 -- IDLE Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 2 | IDLE command (server enters idle) | ✅ Implemented | `ImapSession.handleIdle()`; sends `+ idling`, waits for DONE |
| 2 | Push notifications during IDLE | ✅ Implemented | `IdleNotifier` delivers EXISTS, EXPUNGE, FLAG changes |
| 2 | DONE terminates IDLE | ✅ Implemented | `ImapSession.handleIdle()` reads DONE, sends OK |
| 2 | Client IDLE management | ✅ Implemented | `IdleManager` runs IDLE/DONE loop on virtual thread |

### RFC 2342 -- NAMESPACE Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 5 | NAMESPACE command | ✅ Implemented | `ImapSession.handleNamespace()`; returns personal/other/shared |
| 5 | Personal namespace | ✅ Implemented | `NamespaceConfig` with default `("" "/")` |
| 5 | Other users' namespace | ✅ Implemented | `NamespaceConfig.otherUsers()` (configurable, NIL by default) |
| 5 | Shared namespace | ✅ Implemented | `NamespaceConfig.shared()` (configurable, NIL by default) |

### RFC 7162 -- CONDSTORE Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3.1 | HIGHESTMODSEQ in SELECT | ✅ Implemented | `ImapSession.handleSelect()` reports HIGHESTMODSEQ |
| 3.1 | Per-message modification sequence | ✅ Implemented | `StoredMessage.modSeq()`; updated on every flag change |
| 3.1 | UNCHANGEDSINCE precondition for STORE | ✅ Implemented | `ConditionalStore.conditionalStore()` |
| 3.1 | MODSEQ SEARCH criterion | ✅ Implemented | `SearchCriteria.ModSeq`; `SearchEngine.matches()` |
| 3.1 | Modified UIDs query | ✅ Implemented | `ConditionalStore.modifiedUids()` |

### RFC 5256 -- SORT Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3 | SORT command | ✅ Implemented | `ImapSession.handleSort()`; `SortEngine.sort()` |
| 3 | Sort by ARRIVAL | ✅ Implemented | `SortCriteria.SortKey.ARRIVAL` |
| 3 | Sort by CC | ✅ Implemented | `SortCriteria.SortKey.CC` |
| 3 | Sort by DATE | ✅ Implemented | `SortCriteria.SortKey.DATE` |
| 3 | Sort by FROM | ✅ Implemented | `SortCriteria.SortKey.FROM` |
| 3 | Sort by SIZE | ✅ Implemented | `SortCriteria.SortKey.SIZE` |
| 3 | Sort by SUBJECT (with Re: stripping) | ✅ Implemented | `SortCriteria.SortKey.SUBJECT`; `SortEngine.baseSubject()` |
| 3 | Sort by TO | ✅ Implemented | `SortCriteria.SortKey.TO` |
| 3 | REVERSE sort modifier | ✅ Implemented | `SortCriteria.reverse()` |
| 3 | Combined sort criteria | ✅ Implemented | `SortEngine.buildComparator()` chains comparators |

### RFC 5256 -- THREAD Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3 | THREAD command | ✅ Implemented | `ImapSession.handleThread()` |
| 3 | ORDEREDSUBJECT algorithm | ✅ Implemented | `SortEngine.threadByOrderedSubject()`; groups by base subject, sorts by date |
| 3 | REFERENCES algorithm | ✅ Implemented | `SortEngine.threadByReferences()`; builds tree from References/In-Reply-To headers |

### RFC 6851 -- MOVE Extension

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3 | MOVE command | ✅ Implemented | `ImapSession.handleMove()`; atomic copy + delete + EXPUNGE responses |
| 3 | UID MOVE | ✅ Implemented | UID mode supported via uidMode flag |

### RFC 5258 -- LIST-EXTENDED

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3 | Extended LIST attributes | ⚠️ Partial | `\Subscribed` attribute reported; other extended attributes not implemented |

### RFC 7888 -- LITERAL+

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 4 | Non-synchronizing literals `{N+}` | ✅ Implemented | `ImapLiteral.parseLiteralHeader()` handles `{N+}`; `ImapSession.handleAppend()` |

### RFC 3691 -- UNSELECT

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 2 | UNSELECT command | ✅ Implemented | `ImapSession.handleUnselect()`; returns to Authenticated without expunging |

## Known Limitations
- No TLS/STARTTLS support (neither implicit port 993 nor STARTTLS upgrade)
- AUTHENTICATE only stubs PLAIN mechanism (does not complete challenge-response)
- LIST-EXTENDED partially implemented (only `\Subscribed` attribute)
- No ACL / authorization (RFC 4314) -- all authenticated users share the same mailbox namespace
- No QRESYNC extension (RFC 7162 Section 3.2) -- no UID mapping for reconnection sync
- No COMPRESS extension (RFC 4978)
- No SPECIAL-USE extension (RFC 6154) -- no `\Sent`, `\Drafts`, `\Trash` attributes
- No BINARY extension (RFC 3516)
- MIME body structure is simplified -- multipart parsing delegates to email-common
- Address parsing in ENVELOPE is simplified (wraps raw header value, does not fully parse RFC 5322 addresses)
- Message persistence is in-memory only -- no disk-based durable storage

## Test Coverage Summary
- Total tests: 17
- Key test classes: `ImapCommandTest` (10 tests), `ImapTagTest` (7 tests)
- Sections covered by tests: command parsing, case-insensitive matching, state requirements for all 4 state categories, tag generation/reset/counter, untagged/continuation constants
- Areas needing test expansion: codec (quoting, literals, sequence sets, flags), response parsing, search criteria, sort/thread engine, fetch handler, server session integration, client operations, CONDSTORE, IDLE

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Requirements](REQUIREMENTS.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
