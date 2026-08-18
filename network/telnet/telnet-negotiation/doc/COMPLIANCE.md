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

1. **LINEMODE — SLC values use defaults** — SLC indices 0–12 are parsed and stored
   from peer IS responses. Default mappings are used when not overridden. Full
   interactive reconfiguration of special characters is not yet supported (client
   may override via SLC SET subcommands, but the gateway does not expose a UI for
   changing SLC values at runtime).
2. **NEW_ENV — ESCAPES mask not used** — INFO_ESCAPES (0x02) is defined but
   intentionally not implemented; no Telnet escape character translation is part
   of RFC 1408 environment variables.
3. **NEW_ENV — SCOPE mask not used** — INFO_SCOPE (0x04) is defined but
   intentionally not implemented; environment variables are local-only, no
   cross-host scope tracking is needed.
4. **SUPDUP (RFC 858)** — Not implemented; obsolete protocol, deprecated since
   the late 1980s with no meaningful modern usage.

---

**Last Updated**: 2026-08-18
