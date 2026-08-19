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

### Telnet Commands

| Command | Status |
|---------|--------|
| DM (242) | ✅ Echo back + DmEvent (sync flag) |
| BRK (255) | ✅ CommandEvent fired |
| GA (249) | ✅ CommandEvent fired |
| EC (247) | ✅ CommandEvent fired |
| EL (248) | ✅ CommandEvent fired |
| AYT (246) | ✅ CommandEvent fired |
| IP (244) | ✅ CommandEvent fired |
| NOP (241) | ✅ CommandEvent fired |
| AO (245) | ✅ CommandEvent fired |
| SE (240) | ✅ Handled in subnegotiation |

## RFC 855 — Option Negotiation (Gateway Integration)

| Option | Gateway Behavior | Status |
|--------|-----------------|--------|
| ECHO (1) | Enable/disable echo, default ON | ✅ Implemented |
| SUPPRESS_GO_AHEAD (3) | Auto-negotiate, default ON | ✅ Implemented |
| TTYPE (24) | Respond with terminal type | ✅ Implemented |
| NAWS (31) | Update terminal dimensions | ✅ Implemented |
| BINARY (0) | RFC 856 binary mode with byte translation | ✅ Implemented |
| LINEMODE (34) | Full linemode with line editing (RFC 1143) | ✅ Implemented |
| TERMINAL_SPEED (42) | Respond with local speed | ✅ Implemented |
| NEW_ENV (39) | Provide TERM/COLS/LINES (RFC 1408) | ✅ Implemented |

## RFC 1091 — TTYPE Integration

| Feature | Status |
|---------|--------|
| Respond with registered terminal type | ✅ Implemented |
| TtyEvent on peer type exchange | ✅ Implemented (structured record) |
| TTYPE SEND/IS subnegotiation | ✅ Implemented |

## RFC 1073 — NAWS Integration

| Feature | Status |
|---------|--------|
| Accept remote NAWS size | ✅ Implemented |
| ResizeEvent on terminal resize | ✅ Implemented (structured record) |

## RFC 1079 — Speed Integration

| Feature | Status |
|---------|--------|
| Respond with local terminal speed | ✅ Implemented |
| SpeedHandler creates response IS | ✅ Implemented |

## RFC 1143 — LINEMODE Integration

| Feature | Status |
|---------|--------|
| LINEMODE subnegotiation handling | ✅ Implemented |
| Line editor with character processing | ✅ Implemented |
| SLC (Special Character List) support | ✅ Implemented |
| LineEvent on line submission | ✅ Implemented |
| LinemodeActive/LinodeInactiveEvent | ✅ Implemented |

## RFC 856 — Binary Transmission Mode

| Feature | Status |
|---------|--------|
| Local/remote binary state tracking | ✅ Implemented |
| BinaryEvent on state change (carries local + remote) | ✅ Implemented |
| Inbound byte-level translation (CR NL→LF, CR NUL→CR) | ✅ Implemented |
| Outbound byte-level translation (LF→CR NL) | ✅ Implemented |
| CR lookahead buffer for multi-frame handling | ✅ Implemented |

## RFC 1408 — NEW_ENV Integration

| Feature | Status |
|---------|--------|
| Provide TERM/COLS/LINES with INFOMASK support | ✅ Implemented |
| EnvEvent on remote variable | ✅ Implemented (structured record) |
| NewEnvHandler with full parse (INFO/IS/NO-PRODUCTS) | ✅ Implemented |
| INFOMASK filtering (INFO_TYPE, INFO_LENGTH) | ✅ Implemented |
| BOOL/BYTE type variables | ✅ Implemented |
| Remote environment reading | ✅ Implemented (onRemoteVar callback) |

## Null Safety

| Operation | Status |
|-----------|--------|
| feed(null) | ✅ No-op |
| feed(new byte[0]) | ✅ No-op |
| send(null) | ✅ No-op |
| forTerminal(null) | ✅ Throws NullPointerException |
| addListener(null) | ✅ Throws NullPointerException |

## Terminal Types Supported

| Terminal Type | TTYPE Response | Supported |
|---------------|---------------|-----------|
| VT52 | "vt52" | ✅ |
| VT100 | "vt100" | ✅ |
| VT200 | "vt200" | ✅ |
| VT400 | "vt400" | ✅ |
| VT500 | "vt500" | ✅ |
| ANSI | "ansi" | ✅ |
| XTERM | "xterm" | ✅ |

## Event Model (Sealed Interface)

| Event | Type | Trigger | Data |
|-------|------|---------|------|
| ConnectedEvent | Record | Connection established | — |
| DisconnectedEvent | Record | Connection closed | — |
| NegotiatedEvent | Record | Option negotiation | optionCode |
| ResizeEvent | Record | NAWS update | cols, rows |
| TtyEvent | Record | TTYPE peer type | type |
| CommandEvent | Record | Telnet command | command |
| DmEvent | Record | DM received/sync | isSync |
| BinaryEvent | Record | Binary mode change | localBinary, remoteBinary |
| EnvEvent | Record | NEW_ENV variable | name, variable |
| LineEvent | Record | Line submitted via LINEMODE | line |
| LinemodeActiveEvent | Record | LINEMODE activated | — |
| LinemodeInactiveEvent | Record | LINEMODE deactivated | — |

## Known Limitations

### Single-Connection Scope

**Status**: One Telnet session per gateway instance.

**Reason**: The gateway manages one Telnet session per instance because:
1. The Telnet protocol is inherently session-oriented (a single TCP connection)
2. Multi-connection support is the responsibility of the application layer,
   which creates and manages multiple gateway instances
3. Each gateway instance is self-contained with its own state machine,
   negotiation state, and terminal instance
4. This follows the standard pattern: server socket → accept → create
   gateway per connection (similar to how HTTP servers work)

This is consistent with how Apache MINA SSHD, Netty telnet examples, and
other Java telnet implementations handle connections.

### No Status Queries at Gateway Level

**Status**: Terminal status reporting (like DECRQSS) not implemented at the
gateway level; handled by the terminal emulator directly.

**Reason**: DECRQSS (Device Control String Request Status String) is a terminal
emulator feature, not a telnet protocol feature. The terminal emulator handles
DSR, CPR, DA1, and DECRQSS responses directly. The gateway is a protocol
bridge, not a terminal — it should not duplicate terminal-specific behavior.
Applications that need status queries should access the terminal emulator
directly, which is what the gateway provides via `forTerminal()`.

### Render-on-Write Model

**Status**: Terminal render sent to peer after each data feed; no incremental
render.

**Reason**: This matches the telnet-base design where output is batched per
`feed()` call. An incremental render model would add complexity without
significant benefit for telnet, which operates at line/block granularity
rather than pixel-level rendering. Telnet transmits character-by-character,
so the performance gain from incremental rendering is negligible. The current
model is simpler, more predictable, and fully compliant with the telnet
protocol which expects responses to be sent as they are received.

**Workaround**: For high-throughput scenarios, batch data into larger `feed()`
calls to reduce the number of render → send cycles.

---

**Last Updated**: 2026-08-18
