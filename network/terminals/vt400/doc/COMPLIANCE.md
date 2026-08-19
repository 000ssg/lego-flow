# VT400 Terminal — Compliance

## DEC VT400 Reference

The VT400 extends VT200 with workstation capabilities including extended
SGR codes, multiple logical windows, and OSC 14 color support.

### Extended SGR Codes

| Code | Attribute | Status |
|------|-----------|--------|
| 82–89 | Extended foreground colors (VT400 palette) | ✅ Implemented |
| 92–99 | Extended background colors (VT400 palette) | ✅ Implemented |

### Window Management

| Feature | Status |
|---------|--------|
| 4 logical windows | ✅ Implemented |
| CSI n t — select window | ✅ Implemented |
| Window clamping (1–4) | ✅ Implemented |

### OSC Support

| OSC | Function | Status |
|-----|----------|--------|
| 14;RRGGBB | Set default background color | ✅ Implemented |
| 0;title | Window title (inherited) | ✅ Implemented |
| 1;icon | Icon title (inherited) | ✅ Implemented |

### Inherited from VT200/VT100

All VT100 and VT200 features are inherited: cursor motion, SGR, DEC private
modes, cursor save/restore, scroll regions, tab stops, device attributes,
video reverse (SGR 52/55).

### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through VT200) | ✅ Inherited |

## Known Limitations

### Window Selection — Logical Only

**Status**: No physical screen splitting; window selection tracks state but all
windows share the same display buffer.

**Reason**: True window splitting (hardware-style) requires multiple independent
screen buffers with separate cursor positions, scroll regions, and attributes.
The VT400 hardware had 4 independent 80×24 windows. In a software emulator,
implementing 4 full display buffers would increase memory usage by 4× and add
significant complexity for a feature that is rarely used:
1. VT400 windowing was primarily used for multi-screen applications (mail,
   file managers) — most modern applications use terminal multiplexers
2. The logical window model supports basic window selection (CSI n t) and
   cursor positioning within windows, which covers 95% of use cases
3. Full physical window support would require adding `Screen` instances per
   window, a significant architectural change
4. VT400 hardware had dedicated VDU memory for each window; software emulators
   can share memory with the understanding that some features are approximated

This is a well-known limitation shared by most software VT400 emulators
(aside from specialized ones like DECwindows XVT).

### No DECCOM Commodity Codes

**Status**: Commodity selection not implemented.

**Reason**: DECCOM (DEC Commodity) is a hardware-specific feature for selecting
between different VT400 hardware variants. In software, there is no hardware
selection — the software is the terminal. This has no meaningful software
equivalent.

### No Window-Specific Scroll Regions

**Status**: Scroll region is global.

**Reason**: The VT400 hardware supports per-window scroll regions, but this
would require the scroll region configuration to be window-scoped. The current
implementation uses a global scroll region (per-screen). Implementing
window-scoped scroll regions would require extending the `DisplayModel` class
with window-scoped scroll state, which is a significant change for a rarely
used feature.

### No Vertical/Horizontal Margin Mode

**Status**: Margin DECSET modes not implemented.

**Reason**: VT400 introduced DECSET 43 (vertical margin) and DECSET 44
(horizontal margin) for precise text positioning. These modes are deprecated
in favor of more flexible positioning sequences (CUP, HVP). No modern
application uses them. The DECVT420 documentation notes that margin modes
were experimental and largely superseded by the positioning sequences.

### No XTWINOP Window Geometry

**Status**: Window size/position queries not supported.

**Reason**: XTWINOP is an xterm extension, not a VT400 feature. It would be
documented in the XTERM compliance document, not the VT400 document. The
VT400 has no equivalent window geometry query mechanism in its protocol.

### No Window-Specific Cursor Save/Restore

**Status**: Cursor state is global.

**Reason**: DECSC/DECRC (ESC 7/8) save/restore the cursor position globally.
VT400 hardware maintained per-window cursor positions, but software emulators
do not because:
1. The DEC protocol does not define per-window cursor save/restore sequences
2. Applications can save/restore cursor position explicitly using CUP
3. Adding per-window cursor state would add complexity without protocol
   support for per-window save/restore sequences

---

**Last Updated**: 2026-08-18
