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
| BINARY (0) | Recognized, no translation | ⚠️ Recognized only |

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
| DISCONNECTED | Connection closed | ✅ Implemented |

## Known Limitations

1. **No line-mode editing** — RFC 1116 line discipline not implemented
2. **No BINARY mode translation** — option recognized but byte-level translation not performed
3. **No environment option** — RFC 1408 (NEW_ENVY) not implemented
4. **Single-connection scope** — gateway manages one Telnet session per instance
5. **No status queries** — terminal status reporting (like DECCRCRQSS) not implemented
6. **Render-on-write model** — terminal render sent to peer after each data feed; no incremental render

---

**Last Updated**: 2026-08-17
