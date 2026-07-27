# SMTP Module -- Development Guide

## Module Purpose

The `smtp` module implements the Simple Mail Transfer Protocol (RFC 5321) with ESMTP extensions. It provides both client and server implementations with SASL authentication, STARTTLS, pipelining, chunked transfer (BDAT), and Delivery Status Notifications (DSN). Built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `SmtpClient` -- client with connect, send (DATA/BDAT), STARTTLS, SASL auth, extension negotiation
- `SmtpConnection` -- connection lifecycle: TCP, greeting, EHLO, STARTTLS upgrade, AUTH
- `SmtpServer` -- server with virtual threads, pluggable handler, message store, relay control
- `SmtpSession` -- per-client state machine (GREETING -> READY -> MAIL -> RCPT -> DATA -> QUIT)
- `SmtpCodec` -- text-based codec for command/reply encoding and decoding
- `SmtpHandler` -- pluggable delivery policy (accept/reject senders, recipients, messages, auth)
- `MessageStore` -- pluggable message persistence with `StoreResult`
- `SmtpAuthenticator` -- SASL mechanism interface (PLAIN, LOGIN, CRAM-MD5, XOAUTH2)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | SMTP command/reply codec, ESMTP extension parsing, dot-stuffing (RFC 5321 section 4.5.2), enhanced status codes (RFC 3463) |
| `auth` | SASL authentication mechanisms: PLAIN (RFC 4616), LOGIN (non-standard), CRAM-MD5 (RFC 2195), XOAUTH2 (Google extension) |
| `server` | SMTP server: virtual-thread-per-connection, session state machine, relay config, message store, handler interface |
| `client` | SMTP client: connection management, STARTTLS, SASL auth, DATA and BDAT transfers, extension negotiation, `MessageSubmission` high-level API |
| `dsn` | Delivery Status Notifications: `DeliveryStatus` (RFC 3464 report model), `DsnGenerator` (multipart/report MIME generation for bounce/delay/success) |
| `demo` | Demo applications: simple send, multi-recipient, authenticated SMTP |

## SMTP-Specific Coding Conventions

### Session State Machine
- `GREETING` -- initial state, awaiting client EHLO/HELO
- `READY` -- after EHLO, ready for MAIL FROM or AUTH
- `MAIL` -- after MAIL FROM, expecting RCPT TO
- `RCPT` -- after at least one RCPT TO, expecting DATA/BDAT or more RCPT TO
- `DATA` -- receiving message data (dot-stuffed or BDAT chunks)
- `AUTH` -- in authentication challenge-response exchange
- `QUIT` -- session ending

### ESMTP Extensions Supported
- `SIZE` (RFC 1870) -- message size declaration
- `8BITMIME` (RFC 6152) -- 8-bit MIME transport
- `STARTTLS` (RFC 3207) -- TLS upgrade
- `AUTH` (RFC 4954) -- SASL authentication
- `PIPELINING` (RFC 2920) -- command pipelining
- `CHUNKING` (RFC 3030) -- BDAT chunked transfer
- `DSN` (RFC 3461) -- Delivery Status Notifications
- `ENHANCEDSTATUSCODES` (RFC 2034) -- enhanced status codes (X.Y.Z format)
- `SMTPUTF8` (RFC 6531) -- internationalized email addresses

### Authentication Mechanism Selection
Auto-selection priority: CRAM-MD5 > PLAIN > LOGIN (based on server EHLO capabilities). Can also be explicitly set via `SmtpClientConfig.Builder.authMechanism()`.

### TLS Modes
- `NONE` -- plaintext only
- `STARTTLS` -- upgrade after initial plaintext connection (RFC 3207)
- `IMPLICIT` -- TLS from the start (port 465 style)

### Relay Configuration
- Allowed/blocked sender lists
- Allowed recipient domains
- Required authentication flag
- Maximum message size (default 10 MB)

## Testing Practices

- Protocol codec tests: command encoding/decoding, reply parsing (single-line and multi-line), enhanced status codes
- Dot-stuffing tests: stuff/unstuff round-trips, end-of-data detection, edge cases
- Authentication tests: PLAIN credential encode/decode, LOGIN challenge-response steps
- Extension parsing tests: EHLO response parsing, mechanism list extraction, SIZE limit parsing
- Command enum tests: parse from string, wire form, all 14 commands
- Reply tests: code classification (success/intermediate/transient/permanent), factory methods
- All tests use loopback transport (no external SMTP server required)
- Test count: 201 (148 @Test annotations in module test files + inherited from email/common)

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
