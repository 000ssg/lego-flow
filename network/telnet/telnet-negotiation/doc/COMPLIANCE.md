# Telnet Negotiation Compliance

## RFC 855 — Telnet Option Specifications

| Feature | Status |
|---------|--------|
| 4-state machine | ✅ Implemented |
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

## Known Limitations

1. **No LINEMODE negotiation** — RFC 1116 not implemented
2. **No SUPDUP** — RFC 858 not implemented
3. **No BINARY mode translation** — option recognized but byte-level translation not performed
