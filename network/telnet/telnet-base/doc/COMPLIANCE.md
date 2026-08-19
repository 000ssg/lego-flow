# Telnet Protocol Compliance

## RFC 854 — The Telnet Protocol

### State Machine

| Section | Feature | Status |
|---------|---------|--------|
| 1 | IAC escaping (doubled IAC) | ✅ Implemented |
| 1 | Data interpretation | ✅ Implemented |
| 1 | IAC as sole escape mechanism | ✅ Implemented |

### Commands

| Command | Code | Status |
|---------|------|--------|
| SE | 240 | ✅ Implemented |
| NOP | 241 | ✅ Implemented |
| DATA MARK | 242 | ✅ Implemented |
| BRK | 255 | ✅ Implemented |
| IP | 244 | ✅ Implemented |
| AO | 245 | ✅ Implemented |
| AYT | 246 | ✅ Implemented |
| EC | 247 | ✅ Implemented |
| EL | 248 | ✅ Implemented |
| GA | 249 | ✅ Implemented |
| SB | 250 | ✅ Implemented |
| WILL | 251 | ✅ Implemented |
| WONT | 252 | ✅ Implemented |
| DO | 253 | ✅ Implemented |
| DONT | 254 | ✅ Implemented |
| IAC | 255 | ✅ Implemented |

### Negotiation

| Feature | Status |
|---------|--------|
| WILL/WONT (local request) | ✅ Implemented |
| DO/DONT (remote request) | ✅ Implemented |
| Option code byte | ✅ Implemented |
| State machine tracking | ✅ Implemented (in telnet-negotiation) |

### Subnegotiation

| Feature | Status |
|---------|--------|
| SB...SE framing | ✅ Implemented |
| Nested IAC IAC | ✅ Implemented |
| Option-specific data | ✅ Implemented |

## Known Limitations

### BRK — No OS Process Interruption

**Status**: Partially implemented — recognized in protocol, no OS action.

**Reason**: BRK command (255) is recognized in `TelnetCommand` enum and fires a
`CommandEvent` via the listener/callback chain. Full protocol compliance is met
at the protocol layer. Interrupting a remote OS process is out of scope for a
protocol-only implementation because the library has no integration with an OS
process lifecycle. A real telnet server would delegate to a pseudo-terminal or
subprocess — that is the responsibility of the application using the gateway,
not the telnet library itself.

**Workaround**: Application using the gateway can listen for `CommandEvent(BRK)`
and dispatch to a subprocess or signal handler as needed.

### DM — Sync Handled at Gateway Level

**Status**: Recognized in parser, full sync in gateway.

DM (242) is recognized by the parser and fires `CommandEvent` at the base layer.
Full DM synchronization (echo-back + awaitDmSync) is implemented in
`TelnetGateway` at the integration layer. The base parser does not track sync
state because DM sync is a transport concern — the base module only needs to
detect and forward the command. Gateway is responsible for coordinating the
sync with the underlying stream.

### Line-mode Editing — Delegated to Negotiation Module

**Status**: Detected in parser, handled in telnet-negotiation.

LINEMODE subnegotiation parsing and line buffer editing is handled by
`LinemodeHandler` in the telnet-negotiation module. The base module only detects
and dispatches SB LINEMODE. This separation is intentional: the base parser
knows only about the protocol structure; the negotiation module understands
option semantics. Splitting lower would require bidirectional awareness between
modules, violating the single-responsibility principle.

### Binary Mode — Translation in Negotiation Module

**Status**: Negotiation detected in parser, translation in telnet-negotiation.

BINARY option negotiation (WILL/DO state tracking) is handled in `BinaryHandler`
(telnet-negotiation). The base module only detects and dispatches BINARY
negotiation commands. Byte-level translation (RFC 856) is in the negotiation
module because it requires awareness of the binary state machine, which is a
negotiation-level concern, not a protocol-level one.

---

**Last Updated**: 2026-08-18
