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

1. **BRK — no OS process interruption** — BRK command (255) is recognized in
   TelnetCommand enum and fires a CommandEvent via the listener/callback chain.
   Full protocol compliance is met at the protocol layer; interrupting a remote
   OS process is out of scope for a protocol-only implementation.
2. **DM (Data Mark) — sync handled at gateway level** — DM (242) is recognized
   by the parser and fires CommandEvent at the base layer. Full DM synchronization
   (echo-back + awaitDmSync) is implemented in TelnetGateway at the integration
   layer. The base parser does not track sync state — that is gateway responsibility.
3. **Line-mode editing — in telnet-negotiation** — LINEMODE subnegotiation parsing
   and line buffer editing is handled by LinemodeHandler in the telnet-negotiation
   module. The base module only detects and dispatches SB LINEMODE.
4. **Binary mode — translation in telnet-negotiation** — BINARY option negotiation
   (WILL/DO state tracking) is handled in BinaryHandler (telnet-negotiation).
   The base module only detects and dispatches BINARY negotiation commands.

---

**Last Updated**: 2026-08-18
