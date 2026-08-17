# VT200 Terminal — Compliance

## DEC VT200 Reference

The VT200 is a mechanical terminal that extends VT100 with additional SGR codes.

### All VT100 Features

| Feature | Status |
|---------|--------|
| All VT100 CSI sequences | ✅ Inherited |
| All VT100 SGR codes (0–9, 30–47, 90–107) | ✅ Inherited |
| All VT100 DEC private modes | ✅ Inherited |
| Cursor save/restore, scroll region | ✅ Inherited |

### VT200 Extensions

| Code | Function | Status |
|------|----------|--------|
| 52 | Video Reverse | ✅ Implemented |
| 55 | Video Normal (reverse off) | ✅ Implemented |

### Known Limitations

1. **No mechanical terminal-specific features** — VT200 is primarily a VT100 variant
2. **No DECCOLM (80/132 column mode)** — not implemented
3. **No VT200-specific keypad sequences** — not implemented
