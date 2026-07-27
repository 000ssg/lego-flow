# Syslog Protocol Compliance

## RFC Coverage

### RFC 5424 - The Syslog Protocol
| Section | Feature | Status |
|---------|---------|--------|
| 6.1 | PRI (facility * 8 + severity) | Implemented |
| 6.2.1 | Facility codes (0-23) | Implemented |
| 6.2.1 | Severity levels (0-7) | Implemented |
| 6.2.2 | VERSION field (always 1) | Implemented |
| 6.2.3 | TIMESTAMP (ISO 8601) | Implemented |
| 6.2.4 | HOSTNAME | Implemented |
| 6.2.5 | APP-NAME | Implemented |
| 6.2.6 | PROCID | Implemented |
| 6.2.7 | MSGID | Implemented |
| 6.3 | STRUCTURED-DATA | Implemented |
| 6.3.1 | SD-ID validation | Implemented |
| 6.3.2 | SD-PARAM encoding/escaping | Implemented |
| 6.3.3 | Well-known SD-IDs (timeQuality, origin, meta) | Constants defined |
| 6.4 | MSG (UTF-8) | Implemented |
| 6.4 | BOM prefix | Not implemented |
| 6.5 | NILVALUE ("-") | Implemented |

### RFC 5426 - UDP Transport
| Feature | Status |
|---------|--------|
| One message per datagram | Implemented |
| Default port 514 | Implemented |
| Source IP preservation | Implemented |

### RFC 6587 - TCP Transport
| Feature | Status |
|---------|--------|
| Octet counting framing | Implemented |
| Non-transparent framing (LF) | Implemented |
| Auto-detection (collector) | Implemented |
| Multiple messages per connection | Implemented |

### RFC 5425 - TLS Transport
| Feature | Status |
|---------|--------|
| TLS over TCP | Implemented |
| Default port 6514 | Implemented |
| Octet counting (required) | Implemented |
| Client certificate authentication | Via SSLContext configuration |
| Mutual TLS | Via SSLContext configuration |

## Known Limitations

1. **No RELP protocol** - Reliable Event Logging Protocol not implemented
2. **No message signing** - RFC 5848 (Signed Syslog Messages) not implemented
3. **No BOM prefix** - UTF-8 BOM (0xEF 0xBB 0xBF) not prepended to MSG field
4. **No RFC 3164 (BSD syslog)** - Only RFC 5424 format supported
5. **No rate limiting** - No built-in message rate limiting for senders
6. **No message queuing** - Messages are sent synchronously; no local queue for reliability
