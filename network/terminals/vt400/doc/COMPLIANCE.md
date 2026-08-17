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

#
### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through VT200) | ✅ Inherited |
# Known Limitations

1. **Window selection is logical only** — No physical screen splitting;
   window selection tracks state but all windows share the same display buffer
2. **No DECCOM commodity codes** — Commodity selection not implemented
3. **No window-specific scroll regions** — Scroll region is global
4. **No vertical/horizontal margin mode** — Margin DECSET modes not implemented
5. **No XTWINOP window geometry** — Window size/position queries not supported
6. **No window-specific cursor save/restore** — Cursor state is global

## Verification

| Feature | Test Verification |
|---------|-----------------|
| Extended SGR (82-89/92-99) | `VT400TerminalTest.testExtendedColor` |
| Window selection (CSI n t) | `VT400TerminalTest.testWindowSelection` |
| OSC 14 color | `VT400TerminalTest.testOsc14` |
| Window clamping | `VT400TerminalTest.testWindowClamp` |
| Reset | `VT400TerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
