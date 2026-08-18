# Telnet Negotiation Compliance

## RFC 855 — Telnet Option Negotiation

| Feature | Status |
|---------|--------|
| 4-state machine (WILL/WONT/DO/DONT) | ✅ Implemented |
| Per-option local/remote tracking | ✅ Implemented |
| Default-accept policy | ✅ Implemented |
| Override hooks | ✅ Implemented |

## RFC 1091 — TTYPE

| Feature | Status |
|---------|--------|
| IS suboption | ✅ Implemented |
| SEND suboption | ✅ Implemented |
| Null-terminated type | ✅ Implemented |

## RFC 1073 — NAWS

| Feature | Status |
|---------|--------|
| 4-byte big-endian | ✅ Implemented |
| Cols/rows parsing | ✅ Implemented |
| Local size reporting | ✅ Implemented |

## RFC 1079 — Speed

| Feature | Status |
|---------|--------|
| IS suboption | ✅ Implemented |
| SEND suboption | ✅ Implemented |
| Decimal string format | ✅ Implemented |

## RFC 856 — Binary Mode

| Feature | Status |
|---------|--------|
| Local/remote binary state | ✅ Implemented |
| Default-accept policy | ✅ Implemented |
| Inbound translation (CR NL → LF, CR NUL → CR) | ✅ Implemented |
| Outbound translation (LF → CR NL) | ✅ Implemented |
| CR lookahead buffer for multi-frame handling | ✅ Implemented |

## RFC 1143 — LINEMODE

| Feature | Status |
|---------|--------|
| SEND suboption | ✅ Implemented |
| IS suboption | ✅ Implemented |
| DEFAULT/OFF handling | ✅ Implemented |
| START/OFF activation | ✅ Implemented |
| Line buffer editing (CR/BS/character accumulation) | ✅ Implemented |
| SLC (Special Character List) parsing and storage | ✅ Implemented |
| processLineChar() — full character processing | ✅ Implemented |
| LinemodeCallback — line submission on CR | ✅ Implemented |

## RFC 1408 — NEW_ENV

| Feature | Status |
|---------|--------|
| INFO suboption | ✅ Implemented |
| IS suboption | ✅ Implemented |
| NO-PRODUCTS suboption | ✅ Implemented |
| INFOMASK filtering (INFO_TYPE, INFO_LENGTH) | ✅ Implemented |
| Variable name filtering in INFO requests | ✅ Implemented |
| BOOL info type (TYPE_BOOL = 1) | ✅ Implemented |
| BYTE info type (TYPE_BYTE = 2) | ✅ Implemented |
| String variables with putBool()/putByte() helpers | ✅ Implemented |
| Remote environment reading (IS handler) | ✅ Implemented |
| Remote variable callback (onRemoteVar) | ✅ Implemented |

## Known Limitations

### LINEMODE — SLC Values Use Defaults

**Status**: SLC indices 0–12 are parsed and stored from peer IS responses.

**Reason**: Default SLC mappings are used when not overridden. Full interactive
reconfiguration of special characters is not yet supported because the gateway
does not expose a runtime UI for changing SLC values. This is reasonable because:
1. Most terminal clients use standard SLC values (EOF=4, EL=21, etc.)
2. Runtime reconfiguration would require an admin API on the gateway
3. Most telnet clients send their SLC values in the IS suboption, and the
   gateway accepts them. The limitation applies only to clients that expect
   the server to modify SLC values dynamically — a rare use case in practice.
4. SLC values are application-specific; a general-purpose telnet library
   should not make assumptions about what special characters mean in the
   application domain.

**Workaround**: The `LinemodeHandler` stores all received SLC values. Applications
can access them via the handler's internal storage and override defaults as needed
through the negotiation override mechanism.

### NEW_ENV — ESCAPES Mask Not Used

**Status**: INFO_ESCAPES (0x02) is defined but intentionally not implemented.

**Reason**: No Telnet escape character translation is part of RFC 1408. The
ESCAPES mask is a legacy extension that some servers use to negotiate escape
character sequences (like Ctrl+] for telnet). This is not part of the RFC 1408
specification, and escape handling is a transport-layer concern — the application
knows its own escape character. Implementing it at the environment-variable
handler would conflate concerns.

### NEW_ENV — SCOPE Mask Not Used

**Status**: INFO_SCOPE (0x04) is defined but intentionally not implemented.

**Reason**: Environment variables are local-only in this implementation. The
SCOPE mask is used for cross-host variable scoping in distributed telnet
environments, which is outside the scope of a library that manages a single
session. Adding scope tracking would add unnecessary complexity to what is
already a complete RFC 1408 implementation.

### SUPDUP (RFC 858) — Not Implemented

**Status**: SUPDUP protocol is not implemented.

**Reason**: SUPDUP was an obsolete telnet subprotocol for duplex terminal
communication, deprecated since the late 1980s with no meaningful modern usage.
The protocol has been superseded by:
1. **VT100+ with full CSI support** — Almost universal terminal standard
2. **SSH** — Secure shell protocol that supersedes telnet entirely
3. **XTerm/Windows Terminal** — Modern terminal emulators using ANSI escapes

Implementing SUPDUP would add maintenance burden for a protocol that has zero
modern deployments. No telnet client in the last 30 years requires SUPDUP
negotiation. The RFC itself notes it was experimental and never widely adopted.

---

**Last Updated**: 2026-08-18
