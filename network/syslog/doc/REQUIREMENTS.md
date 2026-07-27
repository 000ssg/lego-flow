# Syslog Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 75
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: RFC 5424 (Syslog Protocol), RFC 5426 (UDP Transport), RFC 6587 (TCP Transport), RFC 5425 (TLS Transport)

---

## Requirements

### Protocol — Message Model (RFC 5424)
1. Represent a complete RFC 5424 syslog message as an immutable record with all header fields
2. Compute PRI value as facility * 8 + severity (range 0-191)
3. Support VERSION field fixed at 1 for RFC 5424
4. Support TIMESTAMP in ISO 8601 format with microsecond precision and UTC offset
5. Support NILVALUE (`-`) for all optional fields (timestamp, hostname, appName, procId, msgId)
6. Enforce field length limits: HOSTNAME (255), APP-NAME (48), PROCID (128), MSGID (32)
7. Provide a builder for incremental message construction with fluent API

### Protocol — Facility and Severity
1. Define all 24 facility codes: 0-15 well-known (KERN through CLOCK), 16-23 local use (LOCAL0 through LOCAL7)
2. Define all 8 severity levels: EMERGENCY(0) through DEBUG(7)
3. Support numeric code lookup with validation for both enums
4. Reject invalid codes with `IllegalArgumentException`

### Protocol — Structured Data (RFC 5424 Section 6.3)
1. Represent SD-ELEMENT as an immutable record with SD-ID and ordered SD-PARAMs
2. Validate SD-ID: must not be null, empty, or contain `=`, `]`, `"`, or space
3. Encode SD-ELEMENT to string format: `[sdID param1="value1" param2="value2"]`
4. Escape SD-PARAM values: prefix `\`, `"`, and `]` with backslash
5. Define constants for well-known SD-IDs: `timeQuality`, `origin`, `meta`
6. Provide builder for incremental parameter construction

### Protocol — Codec (RFC 5424 Encoding/Decoding)
1. Encode `SyslogMessage` to RFC 5424 string format
2. Encode `SyslogMessage` to UTF-8 byte array
3. Decode RFC 5424 string to `SyslogMessage` with full field parsing
4. Decode UTF-8 byte array to `SyslogMessage`
5. Parse PRI field with angle bracket delimiters and range validation (0-191)
6. Parse VERSION with single-digit validation
7. Parse TIMESTAMP using ISO offset date-time format
8. Parse STRUCTURED-DATA: NILVALUE, single element, or multiple elements with escaped param values
9. Throw `SyslogParseException` (unchecked) for malformed messages with descriptive error messages

### Transport — UDP (RFC 5426)
1. Send one syslog message per UDP datagram
2. Default port 514
3. Receive datagrams and decode each as a single syslog message
4. Support binding to specific port or address
5. Run collector on a virtual thread

### Transport — TCP (RFC 6587)
1. Support octet counting framing: message prefixed with byte length and space
2. Support non-transparent framing: message terminated by line feed (LF)
3. Auto-detect framing mode in collector by inspecting first byte (digit = octet counting)
4. Default port 514
5. Handle multiple messages per TCP connection
6. Run collector acceptor and per-connection handlers on virtual threads

### Transport — TLS (RFC 5425)
1. Send syslog messages over TLS-encrypted TCP using octet counting framing (required by RFC 5425)
2. Default port 6514
3. Support custom SSLSocketFactory/SSLServerSocketFactory for certificate configuration
4. Perform TLS handshake on connection establishment
5. Run collector acceptor and per-connection handlers on virtual threads

### High-Level API — SyslogSender
1. Factory methods for UDP, TCP, and TLS transport creation
2. Immutable configuration with `withHostname()` and `withAppName()` returning new instances
3. Auto-populate timestamp with current time on send
4. Auto-detect local hostname for default value
5. Support sending simple messages (facility, severity, text) and messages with structured data
6. Support sending pre-built `SyslogMessage` objects
7. Implement `AutoCloseable` for resource cleanup

### High-Level API — SyslogCollector
1. Builder pattern for configuring multiple transports (UDP and/or TCP)
2. Require at least one transport configured (throw `IllegalStateException` otherwise)
3. Factory methods for single-transport collectors: `udp(port)`, `tcp(port)`
4. Start all configured transports with a single `Consumer<SyslogMessage>` handler
5. Expose local ports for each configured transport
6. Implement `AutoCloseable` for orderly shutdown

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
