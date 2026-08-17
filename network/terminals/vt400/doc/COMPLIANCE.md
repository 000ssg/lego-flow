# VT400 Terminal — Compliance

## DEC VT400/VT420 Reference

The VT400 series adds multi-window support and extended color SGR codes.

### All VT200 Features

| Feature | Status |
|---------|--------|
| All VT100 features | ✅ Inherited |
| All VT200 SGR extensions (52, 55) | ✅ Inherited |

### VT400 Extensions

#### Extended SGR (8-color)

| Code | Function | Status |
|------|----------|--------|
| 82–89 | Extended foreground colors | ✅ Implemented |
| 92–99 | Extended background colors | ✅ Implemented |

#### Window Selection

| Sequence | Function | Status |
|----------|----------|--------|
| CSI 1 t | Select Window 1 (top) | ✅ Implemented |
| CSI 2 t | Select Window 2 (bottom) | ✅ Implemented |
| CSI 3 t | Select Full Screen | ✅ Implemented |

#### Commodity Codes (DECCOM)

| Sequence | Function | Status |
|----------|----------|--------|
| CSI n u | Repeat next character n times | ✅ Implemented |

### Known Limitations

1. **Only 2 windows** — VT400 supports up to 4 logical windows; only 2 implemented
2. **No window-specific scroll regions** — DECSTBM applies to current window only
3. **No S1D1 (split window)** — physical window splitting not implemented
4. **No video attribute per-window** — window attributes shared
5. **No VT400-specific cursor shapes** — not implemented
6. **No margin splitting** — vertical/horizontal margin mode not implemented
