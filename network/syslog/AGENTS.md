# Syslog Module — Development Guide

## Module Purpose

The `syslog` module implements the RFC 5424 structured syslog protocol. It provides message encoding/decoding, plus sender and collector implementations over three transports: UDP (RFC 5426), TCP with framing (RFC 6587), and TLS (RFC 5425). Built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `SyslogSender` — high-level sender with UDP, TCP, and TLS factory methods; auto-populates hostname/appName
- `SyslogCollector` — high-level collector aggregating UDP and TCP listeners with builder API
- `SyslogCodec` — encode/decode RFC 5424 message format (string and byte[] variants)
- `SyslogMessage` — immutable record representing a complete RFC 5424 message with builder
- `StructuredData` — immutable record for SD-ELEMENT with SD-ID and SD-PARAMs
- `Facility` — enum of all 24 facility codes (kern through local7)
- `Severity` — enum of all 8 severity levels (emergency through debug)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | RFC 5424 message model and codec: `SyslogMessage`, `SyslogCodec`, `Facility`, `Severity`, `StructuredData`, `SyslogParseException` |
| `transport` | Transport implementations: `UdpSender`/`UdpCollector` (RFC 5426), `TcpSender`/`TcpCollector` (RFC 6587), `TlsSender`/`TlsCollector` (RFC 5425), `FramingMode` enum |
| _(root)_ | High-level APIs: `SyslogSender`, `SyslogCollector` |

## Syslog-Specific Coding Conventions

### Message Format (RFC 5424)
```
<PRI>VERSION TIMESTAMP HOSTNAME APP-NAME PROCID MSGID STRUCTURED-DATA MSG
```

- **PRI** = facility * 8 + severity (0-191)
- **VERSION** = 1 (always, for RFC 5424)
- **NILVALUE** = `-` (used for absent optional fields)
- **TIMESTAMP** = ISO 8601 with microsecond precision and UTC offset

### Facility Codes (24 values, 0-23)
- 0-15: well-known (KERN, USER, MAIL, DAEMON, AUTH, SYSLOG, LPR, NEWS, UUCP, CRON, AUTHPRIV, FTP, NTP, AUDIT, ALERT, CLOCK)
- 16-23: local use (LOCAL0 through LOCAL7)

### Severity Levels (8 values, 0-7)
- EMERGENCY(0), ALERT(1), CRITICAL(2), ERROR(3), WARNING(4), NOTICE(5), INFO(6), DEBUG(7)

### Structured Data Escaping
- SD-ID must not contain `=`, `]`, `"`, or space
- SD-PARAM values escape `\`, `"`, and `]` with backslash prefix

### Transport Framing Modes (TCP, RFC 6587)
- **OCTET_COUNTING**: `N<SP>message` where N is byte length
- **NON_TRANSPARENT**: `message<LF>` terminated by line feed
- TcpCollector auto-detects framing: digit first byte = octet counting, otherwise non-transparent

### Field Length Limits (RFC 5424)
- HOSTNAME: max 255 characters
- APP-NAME: max 48 characters
- PROCID: max 128 characters
- MSGID: max 32 characters

## Thread Safety Model

- `SyslogCollector` uses virtual threads for concurrent connection handling
- `TcpCollector` spawns one virtual thread per accepted TCP connection
- `TlsCollector` spawns one virtual thread per accepted TLS connection
- `UdpCollector` runs a single virtual thread reading datagrams
- `volatile boolean running` flag controls collector lifecycle
- All transport classes implement `AutoCloseable`

## Sealed Type Usage

- `SyslogSender.Transport` is a sealed interface permitting `UdpTransport`, `TcpTransport`, `TlsTransport` (all private records)

## Testing Practices

- Codec tests: encode/decode round-trip for all message fields and edge cases
- Structured data tests: builder, escaping, validation, well-known SD-IDs
- Facility/Severity tests: code lookup, validation, boundary values
- Transport tests: UDP send/receive, TCP with both framing modes, framing mode enum
- Integration tests: SyslogSender and SyslogCollector with loopback transport
- Parse exception tests: error messages and cause chaining
- All tests use loopback transport (no external syslog server required)
- Test count: 75

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
