# VT500 Terminal — Compliance

## DEC VT500/VT520 Reference

The VT500 series adds DEC character set support and DCS for user-defined character sets.

### All VT400 Features

| Feature | Status |
|---------|--------|
| All VT200 features | ✅ Inherited |
| All VT400 window support | ✅ Inherited |
| All VT400 extended SGR | ✅ Inherited |

### VT500 Extensions

#### DEC Character Set Selection

| Feature | Status |
|---------|--------|
| DECSET for character set modes | ✅ Implemented |
| DCS for user-defined character sets | ✅ Implemented |
| SO/SI character set switching | ✅ Implemented |

#### Window Host Commands

| Feature | Status |
|---------|--------|
| Window selection (CSI n t) | ✅ Inherited from VT400 |
| Window host command routing | ✅ Implemented |

### Known Limitations

1. **No full DEC Special Character set** — only basic G0/G1 switching
2. **No LINE DRAW character set** — not implemented
3. **No Greek character set** — not implemented
4. **No Typography set** — not implemented
5. **User-defined character sets are single-char** — full DCS definitions not supported
6. **No VT520-specific extensions** — VT520 additional features not implemented
7. **No multiple character set (MCS)** — not implemented
