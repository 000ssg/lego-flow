# SMTP Module -- Requirements

This document tracks the requirements, design decisions, and evolution of the SMTP module.

---

## Commit: Initial Implementation -- SMTP Client, Server, and Protocol (2026-07-04)

### Original Request

> Implement SMTP (RFC 5321) module with complete client and server implementations, ESMTP extensions, SASL authentication, STARTTLS, and Delivery Status Notifications.

### Reformulated Requirements

1. Implement SMTP command/reply codec per RFC 5321 text-based format
2. Support all core SMTP commands: EHLO, HELO, MAIL, RCPT, DATA, RSET, QUIT, NOOP, VRFY, EXPN, HELP
3. Support ESMTP extensions: SIZE (RFC 1870), 8BITMIME (RFC 6152), STARTTLS (RFC 3207), AUTH (RFC 4954), PIPELINING (RFC 2920), CHUNKING/BDAT (RFC 3030), DSN (RFC 3461), ENHANCEDSTATUSCODES (RFC 2034), SMTPUTF8 (RFC 6531)
4. Implement SASL authentication mechanisms: PLAIN (RFC 4616), LOGIN, CRAM-MD5 (RFC 2195), XOAUTH2
5. Implement dot-stuffing transparency per RFC 5321 section 4.5.2
6. Implement enhanced status codes per RFC 3463 (X.Y.Z format with class/subject/detail)
7. Implement SMTP server with virtual threads, pluggable handler, pluggable message store, relay restrictions
8. Implement SMTP client with STARTTLS, SASL auth, DATA and BDAT transfer modes
9. Implement high-level `MessageSubmission` API for one-shot sends
10. Implement Delivery Status Notifications (RFC 3464) for bounce, delay, and success reports
11. Provide demo applications for common SMTP scenarios
12. JDK-only implementation -- no external dependencies beyond SLF4J
13. Comprehensive test coverage for protocol, authentication, and codec layers

### Final Design Decisions

- **Text-based codec**: SMTP is line-oriented text; `SmtpCodec` utility class with static methods for encode/decode (no instance state needed)
- **Enum-based commands**: `SmtpCommand` enum for all 14 commands; `SmtpExtension` enum for all 9 ESMTP extensions with keyword mapping
- **Record for enhanced codes**: `EnhancedStatusCode` as a record with validation, predefined constants for common codes, and parse/wireForm methods
- **Session state machine**: `SmtpSession` with explicit `State` enum tracking the protocol state; command dispatch via switch expression
- **Virtual threads for server**: `Executors.newVirtualThreadPerTaskExecutor()` for one-thread-per-connection model, matching the blocking I/O style of SMTP sessions
- **Builder pattern for config**: `SmtpClientConfig.Builder` and `RelayConfig.Builder` for immutable configuration with sensible defaults
- **Interface segregation**: `SmtpHandler` for delivery policy, `MessageStore` for persistence, `SmtpAuthenticator` for auth mechanisms -- all independently pluggable
- **DSN as MIME generation**: `DsnGenerator` produces complete multipart/report messages per RFC 3464 format

### Implementation Details

- **Files created**: 31 Java source files across 6 packages (protocol, auth, server, client, dsn, demo)
- **Protocol package** (6 classes): `SmtpCommand` (14 commands), `SmtpReply` (3-digit codes + enhanced codes + multi-line), `SmtpCodec` (encode/decode commands and replies), `SmtpExtension` (9 extensions with EHLO parsing), `EnhancedStatusCode` (RFC 3463 record with 18 predefined constants), `DotStuffing` (stuff/unstuff with end-of-data detection)
- **Auth package** (6 classes): `SmtpAuthenticator` (interface), `PlainAuth`, `LoginAuth`, `CramMd5Auth`, `XOAuth2Auth`, `SmtpAuthException`
- **Server package** (8 classes): `SmtpServer`, `SmtpSession` (state machine), `SmtpHandler` (interface with `acceptAll()` and `forDomains()` factories), `MessageStore` (interface with `StoreResult` record), `InMemoryMessageStore`, `MailEnvelope` (sender + recipients + data + params), `RelayConfig` (builder with domain/sender/auth restrictions), `MessageStoreException`
- **Client package** (5 classes): `SmtpClient` (high-level AutoCloseable), `SmtpConnection` (lifecycle management), `SmtpClientConfig` (builder with TlsMode enum), `SmtpException`, `MessageSubmission` (static one-shot API with `DeliveryResult` record)
- **DSN package** (2 classes): `DeliveryStatus` (report model with `Action`, `NotifyType`, `ReturnType` enums and `RecipientStatus` record), `DsnGenerator` (multipart/report MIME builder)
- **Demo package** (3 classes): `SimpleSmtpDemo`, `MultiRecipientDemo`, `AuthSmtpDemo`

### Test Coverage

- Protocol tests: `SmtpCommandTest`, `SmtpReplyTest`, `SmtpCodecTest`, `SmtpExtensionTest`, `EnhancedStatusCodeTest`, `DotStuffingTest`
- Auth tests: `PlainAuthTest`, `LoginAuthTest`
- Total tests: 201 (148 @Test annotations in module + inherited from email/common)

### Cost Estimate

| Metric | Value |
|--------|-------|
| Background agents | 1 |
| Agent tokens | ~50,000 |
| Agent tool calls | ~40 |
| Agent wall time | ~15 min |
| Files created/modified | 31 created |
| Lines added/removed | +3,200 / -0 |
| Tests added | 201 (total: 201) |

---

**Last Updated**: 2026-07-06
