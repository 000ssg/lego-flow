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

## RFC 1143 — LINEMODE

| Feature | Status |
|---------|--------|
| SEND suboption | ✅ Implemented |
| IS suboption | ✅ Implemented |
| DEFAULT/OFF handling | ✅ Implemented |
| Line buffer editing | ❌ Not implemented (known limitation) |

## RFC 1408 — NEW_ENV

| Feature | Status |
|---------|--------|
| INFO suboption | ✅ Implemented |
| IS suboption | ✅ Implemented |
| NO-PRODUCTS suboption | ✅ Implemented |
| INFOMASK filtering | ❌ Not implemented (known limitation) |
| BOOL info type | ❌ Not implemented (known limitation) |

## Known Limitations

1. **No full LINEMODE line discipline** — LINEMODE IS response sends default mode, but no line buffer editing
2. **No SUPDUP** — RFC 858 not implemented
3. **NEW_ENV: No INFOMASK filtering** — all variables always sent
4. **NEW_ENV: No BOOL info type** — only STRING variables supported
