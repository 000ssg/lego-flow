# Terminal Base — Compliance

## Escape Sequence Parsing

### ECMA-48 / ISO 6429 (Control Functions for Coded Character Sets)

| Section | Feature | Status |
|---------|---------|--------|
| 8.1.1 | CSI (ESC [) parsing | ✅ Implemented |
| 8.1.1 | Parameter bytes (0x30–0x3F) | ✅ Implemented |
| 8.1.1 | Intermediate bytes (0x20–0x2F) | ✅ Implemented |
| 8.1.1 | Final byte (0x40–0x7E) | ✅ Implemented |
| 8.1.2 | DCS (ESC P) strings | ✅ Implemented |
| 8.1.3 | OSC (ESC ]) strings | ✅ Implemented |
| 8.1.4 | APC (ESC _) strings | ✅ Implemented |
| 8.1.5 | PM (ESC ^) strings | ✅ Implemented |
| 8.2 | String Terminator (ST = ESC \) | ✅ Implemented |
| 8.2 | BEL terminator for OSC | ✅ Implemented |
| 9.1 | CSI parameter separator ';' | ✅ Implemented |
| 9.2 | CSI subparameter separator ':' | ✅ Implemented (flat list) |

### Control Characters

| Character | Code | Feature | Status |
|-----------|------|---------|--------|
| NUL | 0x00 | Null (ignored) | ✅ Implemented |
| BS | 0x08 | Backspace | ✅ Implemented |
| HT | 0x09 | Horizontal Tab | ✅ Implemented |
| LF | 0x0A | Line Feed | ✅ Implemented |
| VT | 0x0B | Vertical Tab (→ LF) | ✅ Implemented |
| CR | 0x0D | Carriage Return | ✅ Implemented |
| ESC | 0x1B | Escape introducer | ✅ Implemented |

### Display Model

| Feature | Status |
|---------|--------|
| 2D character grid | ✅ Implemented |
| Cursor position (row, col) | ✅ Implemented |
| Scroll region | ✅ Implemented |
| Auto-wrap tracking | ✅ Implemented |
| Saved cursor (DECSC/DECRC) | ✅ Implemented |

### Known Limitations

1. **No G0/G1 character set switching** — SSO/SI/SCSI handled at terminal variant level
2. **No DECSLRG (large characters)** — parser recognizes but does not implement
3. **Tab stop management** — implicit (every 8 columns); no explicit tab clear/set
4. **No device reporting** — DECRQCRA, DSR queries not implemented
