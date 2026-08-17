# Telnet Gateway Compliance

## RFC 854 — The Telnet Protocol (Bridge Layer)

### IAC Handling

| Feature | Status |
|---------|--------|
| Strip IAC commands from inbound data | ✅ Implemented |
| Auto-escape IAC bytes in outbound data | ✅ Implemented |
| IAC IAC → literal IAC in subnegotiation | ✅ Implemented (via telnet-base) |

### Data Flow

| Direction | Feature | Status |
|-----------|---------|--------|
| Peer → Terminal | Strip IAC commands, feed clean data | ✅ Implemented |
| Terminal → Peer | IAC escape output bytes | ✅ Implemented |
| Peer → Terminal | Auto-flush after each feed | ✅ Implemented |

## RFC 855 — Option Negotiation (Gateway Integration)

| Option | Gateway Behavior | Status |
|--------|-----------------|--------|
| ECHO (1) | Enable/disable echo, default ON | ✅ Implemented |
| SUPPRESS_GO_AHEAD (3) | Auto-negotiate, default ON | ✅ Implemented |
| TTYPE (24) | Respond with terminal type | ✅ Implemented |
| NAWS (31) | Update terminal dimensions | ✅ Implemented |
| BINARY (0) | RFC 856 binary mode state tracking | ✅ Implemented |
| LINEMODE (32) | Send default LINEMODE IS response (RFC 1143) | ✅ Implemented |
| NEW_ENV (39) | Provide TERM/COLS/LINES (RFC 1408) | ✅ Implemented |

## RFC 1091 — TTYPE Integration

| Feature | Status |
|---------|--------|
| Respond with registered terminal type | ✅ Implemented |
| TTYPE_EXCHANGED event | ✅ Implemented |

## RFC 1073 — NAWS Integration

| Feature | Status |
|---------|--------|
| Accept remote NAWS size | ✅ Implemented |
| RESIZED event on terminal resize | ✅ Implemented |

## RFC 1079 — Speed Integration

| Feature | Status |
|---------|--------|
| Respond with local terminal speed | ✅ Implemented |

## RFC 856 — Binary Transmission Mode

| Feature | Status |
|---------|--------|
| Local/remote binary state tracking | ✅ Implemented |
| BINARY_NEGOTIATED event | ✅ Implemented |
| Byte-level translation | ❌ Not implemented (known limitation) |

## RFC 1408 — NEW_ENV Integration

| Feature | Status |
|---------|--------|
| Provide TERM/COLS/LINES | ✅ Implemented |
| ENV_EXCHANGED event | ✅ Implemented |
| INFOMASK filtering | ❌ Not implemented (known limitation) |
| Remote environment reading | ❌ Not implemented (known limitation) |

## Terminal Protocol Mapping

| Terminal Type | TTYPE Response | Supported |
|---------------|---------------|-----------|
| VT52 | "vt52" | ✅ |
| VT100 | "vt100" | ✅ |
| VT200 | "vt200" | ✅ |
| VT400 | "vt400" | ✅ |
| VT500 | "vt500" | ✅ |
| ANSI | "ansi" | ✅ |
| XTERM | "xterm" | ✅ |

## Event Model

| Event | Trigger | Status |
|-------|---------|--------|
| CONNECTED | Connection established | ✅ Implemented |
| NEGOTIATED | Option negotiation completed | ✅ Implemented |
| RESIZED | NAWS update received | ✅ Implemented |
| TTYPE_EXCHANGED | TTYPE exchange complete | ✅ Implemented |
| BINARY_NEGOTIATED | BINARY mode negotiated | ✅ Implemented |
| ENV_EXCHANGED | NEW_ENV exchange complete | ✅ Implemented |
| DISCONNECTED | Connection closed | ✅ Implemented |

## Known Limitations

1. **No line-mode editing** — RFC 1116 line discipline not implemented; LINEMODE sends default IS response only
2. **No BINARY byte-level translation** — binary mode state is tracked but 8-bit translation not performed
3. **NEW_ENV: No INFOMASK filtering** — environment variables always provided regardless of peer's INFO request
4. **NEW_ENV: No remote environment reading** — only provides local variables, does not parse remote environment
5. **Single-connection scope** — gateway manages one Telnet session per instance
6. **No status queries** — terminal status reporting (like DECRQSS) not implemented
7. **Render-on-write model** — terminal render sent to peer after each data feed; no incremental render

---

**Last Updated**: 2026-08-18
