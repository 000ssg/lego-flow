# VT100 Terminal — Compliance

## DEC VT100 Reference

### CSI Cursor Motion

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n A | A | Cursor Up n | ✅ Implemented |
| CSI n B | B | Cursor Down n | ✅ Implemented |
| CSI n C | C | Cursor Forward n | ✅ Implemented |
| CSI n D | D | Cursor Back n | ✅ Implemented |
| CSI n E | E | Cursor Next Line n | ✅ Implemented |
| CSI n F | F | Cursor Prev Line n | ✅ Implemented |
| CSI n G | G | Cursor Horizontal Absolute | ✅ Implemented |
| CSI r;c H | H | Cursor Position | ✅ Implemented |
| CSI r;c f | f | Horizontal Vertical Position | ✅ Implemented |
| CSI n d | d | Cursor Vertical Absolute | ✅ Implemented |

### CSI Display Operations

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n J | J | Erase in Display | ✅ Implemented (0, 1, 2) |
| CSI n K | K | Erase in Line | ✅ Implemented (0, 1, 2) |
| CSI n L | L | Insert Line | ✅ Implemented |
| CSI n M | M | Delete Line | ✅ Implemented |
| CSI n P | P | Delete Character | ✅ Implemented |
| CSI n @ | @ | Insert Blank | ✅ Implemented |
| CSI n X | X | Erase Character | ✅ Implemented |
| CSI n b | b | Repeat Preceding (EUT) | ✅ Implemented |

### SGR (Select Graphic Rendition)

| Code | Attribute | Status |
|------|-----------|--------|
| 0 | Reset all | ✅ Implemented |
| 1 | Bold | ✅ Implemented |
| 2 | Dim | ✅ Implemented |
| 4 | Underline | ✅ Implemented |
| 5 | Blink | ✅ Implemented |
| 7 | Reverse | ✅ Implemented |
| 8 | Hidden | ✅ Implemented |
| 9 | Strikethrough | ✅ Implemented |
| 30–37 | Foreground colors | ✅ Implemented |
| 40–47 | Background colors | ✅ Implemented |
| 90–97 | Bright foreground | ✅ Implemented |
| 100–107 | Bright background | ✅ Implemented |

### DEC Private Modes (DECSET/DECRST)

| Mode | Number | Function | Status |
|------|--------|----------|--------|
| DECCM | 1 | Application Keypad | ✅ Implemented |
| DECORM | 6 | Origin Mode | ✅ Implemented |
| DECAWM | 7 | Auto Wrap | ✅ Implemented |

### Device Attributes

| Sequence | Function | Status |
|----------|----------|--------|
| CSI ? c | DA1 response | ✅ Implemented |

### Cursor Save/Restore

| Sequence | Function | Status |
|----------|----------|--------|
| CSI 7 | DECSC (save cursor) | ✅ Implemented |
| CSI 8 | DECRC (restore cursor) | ✅ Implemented |
| CSI s | DECSC (alternate) | ✅ Implemented |
| CSI u | DECRC (alternate) | ✅ Implemented |

### Scroll Region

| Sequence | Function | Status |
|----------|----------|--------|
| CSI r;t B | DECSTBM | ✅ Implemented |

### Known Limitations

1. **No device reporting queries** — DSR (CSI n c), DECSCUSR not implemented
2. **No margin bell** — not implemented
3. **No printer** — DA2/DA3 not implemented
4. **No SSO/SI character set switching** — G0/G1 not implemented
5. **DECSTBM with inverted range** — not validated
6. **No line drawing character set** — US/SCS not supported
