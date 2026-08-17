# XTERM Terminal — Compliance

## XTERM Extension Reference

The XTERM terminal extends ANSI with modern terminal features including
256/true color, mouse tracking, bracketed paste, and various DEC private
modes. Unlike the ANSI terminal, XTERM re-enables DEC private modes for
xterm-specific extensions.

### Extended Color

| Feature | Sequence | Status |
|---------|----------|--------|
| 256-color foreground | CSI 38;5;n m | ✅ Implemented |
| 256-color background | CSI 48;5;n m | ✅ Implemented |
| True color foreground | CSI 38;2;r;g;b m | ✅ Implemented |
| True color background | CSI 48;2;r;g;b m | ✅ Implemented |

### Underline Styles

| Style | Sequence | Status |
|-------|----------|--------|
| None (0) | CSI 4:0 m | ✅ Implemented |
| Single (1) | CSI 4:1 m | ✅ Implemented |
| Double (2) | CSI 4:2 m | ✅ Implemented |
| Curly (3) | CSI 4:3 m | ✅ Implemented |
| Dotted (4) | CSI 4:4 m | ✅ Implemented |
| Dashed (5) | CSI 4:5 m | ✅ Implemented |
| Plain underline | CSI 4 m | ✅ Implemented |

### Text Decoration

| Code | Attribute | Status |
|------|-----------|--------|
| 53 | Overline on | ✅ Implemented |
| 55 | Overline off | ✅ Implemented |

### Mouse Tracking

| Mode | DECSET | Function | Status |
|------|--------|----------|--------|
| OFF | (default) | No mouse tracking | ✅ Implemented |
| NORMAL | 1000 | Button event tracking | ✅ Implemented |
| HIGHLIGHT | 1002 | Highlight tracking | ✅ Implemented |
| CELL_MOTION | 1003 | All motion tracking | ✅ Implemented |
| SGR extended | 1006 | SGR extended mouse | ✅ Implemented |
| URXVT mode | 1015 | URXVT mouse encoding | ✅ Implemented |
| SGR+URXVT | 1016 | SGR + URXVT combined | ✅ Implemented |

### Modern Features

| Feature | DECSET | Status |
|---------|--------|--------|
| Bracketed paste | 2004 | ✅ Implemented |
| Synchronized output | 2026 | ✅ Implemented |
| Focus event tracking | 1004 | ✅ Implemented |

### Cursor Shape (DECSCUSR)

| Style | Code | Sequence | Status |
|-------|------|----------|--------|
| Default | 0 | CSI 0 SP q | ✅ Implemented |
| Blinking block | 1 | CSI 1 SP q | ✅ Implemented |
| Steady block | 2 | CSI 2 SP q | ✅ Implemented |
| Blinking underline | 3 | CSI 3 SP q | ✅ Implemented |
| Steady underline | 4 | CSI 4 SP q | ✅ Implemented |
| Blinking bar | 5 | CSI 5 SP q | ✅ Implemented |
| Steady bar | 6 | CSI 6 SP q | ✅ Implemented |

### OSC Support

| OSC | Function | Status |
|-----|----------|--------|
| 0;title | Window title | ✅ Implemented |
| 1;icon | Icon title | ✅ Implemented |
| 2;title | Window title | ✅ Implemented |
| 52;target;data | Clipboard manipulation | ✅ Implemented (write only) |
| 10;color | Foreground color query | ✅ Recognized |
| 11;color | Background color query | ✅ Recognized |
| 12;color | Cursor color query | ✅ Recognized |
| 7;uri | Current working directory | ✅ Recognized (no action) |

### DCS Support

| DCS | Function | Status |
|-----|----------|--------|
| DECRQSS | Request status string | ✅ Recognized (limited response) |

### DEC Private Modes Re-enabled

XTERM re-enables DEC private modes that were filtered by the ANSI parent:

| Mode | Name | Function | Status |
|------|------|----------|--------|
| 1 | DECCM | Application cursor keys | ✅ Implemented |
| 5 | DECSCNM | Reverse video | ✅ Implemented |
| 6 | DECORM | Origin mode | ✅ Implemented |
| 7 | DECAWM | Auto-wrap | ✅ Implemented |

#
### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through ANSI) | ✅ Inherited |
# Known Limitations

1. **Mouse reports are stateful only** — No actual mouse event generation;
   mode state is tracked but mouse reports must come from the transport layer
2. **No XTWINOP** — Window geometry queries not implemented
3. **No permitWindowOps** — DECSET 1003/1010/1011 not implemented
4. **No debug mode** — DECSET 1010 not implemented
5. **No send escape sequence back** — DECSET 1011 not implemented
6. **No multimedia keys** — DECSET 1030+ not implemented
7. **Clipboard read not supported** — Only write (OSC 52) supported
8. **SGR 58/59 (border color)** — Not supported
9. **SGR 58;5;n / 59;5;n / 58;2;r;g;b / 59;2;r;g;b** — Border color not supported
10. **CSI 4;1 m ambiguity** — Since CSIParams flattens ; and : separators,
    CSI 4;1 m (underline + bold) and CSI 4:1 m (underline style 1) are
    indistinguishable. Values 0-5 are treated as style subparams.
11. **DECRQSS responds with limited subset** — Only mouse, bracketed paste,
    sync, and cursor shape modes are recognized

## Verification

| Feature | Test Verification |
|---------|-----------------|
| 256/true color | `XTERMTerminalTest.test256Color`, `testTrueColor` |
| Mouse tracking | `XTERMTerminalTest.testMouseMode` |
| Bracketed paste | `XTERMTerminalTest.testBracketedPaste` |
| Sync mode | `XTERMTerminalTest.testSyncMode` |
| DECSCUSR | `XTERMTerminalTest.testCursorShape` |
| OSC 52 clipboard | `XTERMTerminalTest.testClipboard` |
| Underline styles | `XTERMTerminalTest.testUnderlineStyle` |
| Overline | `XTERMTerminalTest.testOverline` |
| Focus tracking | `XTERMTerminalTest.testFocusTracking` |
| Reset | `XTERMTerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
