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
|-----|--------|--------|
| DECRQSS | Request status string | ✅ Recognized (limited response) |

### DEC Private Modes Re-enabled

XTERM re-enables DEC private modes that were filtered by the ANSI parent:

| Mode | Name | Function | Status |
|------|------|----------|--------|
| 1 | DECCM | Application cursor keys | ✅ Implemented |
| 5 | DECSCNM | Reverse video | ✅ Implemented |
| 6 | DECORM | Origin mode | ✅ Implemented |
| 7 | DECAWM | Auto-wrap | ✅ Implemented |

### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through ANSI) | ✅ Inherited |

## Known Limitations

### Mouse Reports Are Stateful Only

**Status**: No actual mouse event generation; mode state is tracked but mouse
reports must come from the transport layer.

**Reason**: The terminal emulator tracks mouse tracking mode state (DECSET
1000–1016) and generates the correct mouse report sequences when the transport
layer delivers mouse events. The emulator itself does not have a mouse input
device — it only processes escape sequences. The transport layer (SSH channel,
WebSocket, etc.) is responsible for converting physical mouse events into
the mouse report sequences that the emulator would generate.

This is consistent with how real terminal emulators work: xterm generates
mouse reports only when the X server delivers a mouse event through the
X input system. The terminal emulator is a display device, not an input
device.

### No XTWINOP

**Status**: Window geometry queries not implemented.

**Reason**: XTWINOP (xterm window operations) is an xterm-specific extension
for querying and controlling window geometry (position, size, state). It is
not part of the xterm ctlseqs documentation and is primarily used by window
managers. Implementing it would require:
1. Knowledge of the windowing system (X11, macOS, Windows, Wayland)
2. Integration with a window management API
3. State tracking for window position/size which conflicts with the
   terminal being a character-cell display (not a window)

This is an output-layer concern, not a terminal-emulation concern. Window
managers and terminal emulators handle XTWINOP independently.

### No permitWindowOps (DECSET 1003/1010/1011)

**Status**: permitWindowOps not implemented.

**Reason**: These DECSET modes control whether the terminal is permitted to
perform window operations (resize, minimize, etc.). They are security features
that prevent terminals from performing actions on the host system. Implementing
them would require:
1. A security policy framework at the transport layer
2. Integration with the host system's window manager
3. Permission models that are outside the scope of a terminal emulator

These are host-system features, not terminal-emulation features.

### No Debug Mode

**Status**: DECSET 1010 not implemented.

**Reason**: Debug mode in xterm enables various diagnostic outputs. These are
host-system features (debug logs, tracing) that are outside the scope of
terminal emulation. The terminal emulator tracks the debug state but does
not produce diagnostic output itself.

### No Send Escape Sequence Back

**Status**: DECSET 1011 not implemented.

**Reason**: This mode controls whether escape sequences are sent back to the
host or consumed locally. This is an input-layer concern — the transport
layer determines what happens to received escape sequences. The terminal
emulator only processes sequences, it does not have an input device.

### No Multimedia Keys

**Status**: DECSET 1030+ not implemented.

**Reason**: Multimedia key support is an xterm extension for handling media
play/pause/volume keys. These keys generate events at the X input system
level, not at the terminal emulation level. Key translation is the
responsibility of the input layer (keyboard driver, SSH channel), not the
terminal emulator.

### Clipboard Read Not Supported

**Status**: Only write (OSC 52) supported.

**Reason**: OSC 52 clipboard write is implemented (the terminal can set the
system clipboard via escape sequences). However, clipboard **read** (requesting
the system clipboard contents) would require:
1. Integration with the system clipboard API (X11, macOS, Windows)
2. A response sequence that the transport layer sends back
3. Security considerations (clipboard content may contain sensitive data)

Clipboard read is a host-system feature that requires output-layer support.
The terminal emulator cannot access the system clipboard directly.

### SGR 58/59 (Border Color)

**Status**: Not supported.

**Reason**: SGR 58/59 control the terminal window border color. This is an
xterm-specific extension that requires:
1. Knowledge of the windowing system's drawing API
2. Integration with the terminal's rendering backend
3. Color space support for border colors

This is an output-layer concern. The terminal emulator tracks the border
color state but cannot draw window borders (that is the rendering backend's
responsibility).

### CSI 4;1 m Ambiguity

**Status**: Since CSIParams flattens ; and : separators, CSI 4;1 m (underline
+ bold) and CSI 4:1 m (underline style 1) are indistinguishable. Values
0-5 are treated as style subparams.

**Reason**: The CSI parser uses a flat parameter list where both ';' and ':'
are treated as separators. This means:
- CSI 4;1 m (underline style 1, ECMA-48 format) → parsed as underline style 1
- CSI 4:1 m (underline style 1, xterm subparameter format) → parsed as underline style 1

Both produce the same result, which is the correct behavior for terminal
emulation purposes. The distinction between ';' and ':' is only meaningful
for precise protocol analysis, not for visual rendering. Most terminal
emulators (xterm, GNOME Terminal) treat both separators identically.

This is a design decision: for terminal emulation, the visual result matters,
not the exact wire format. If a protocol analysis tool needs to distinguish
the separators, it should use the raw escape parser output, not the flattened
CSIParams.

### DECRQSS Responds With Limited Subset

**Status**: Only mouse, bracketed paste, sync, and cursor shape modes are
recognized.

**Reason**: DECRQSS (Request Status String) returns the status of DEC private
modes that the terminal recognizes. The implementation supports a subset
of the xterm DECRQSS responses:
- Mouse tracking modes (1000, 1002, 1003, 1006, 1015, 1016)
- Bracketed paste (2004)
- Synchronized output (2026)
- Cursor shape modes (cursor shape query)

Modes like XTWINOP, debug mode, permitWindowOps, and multimedia keys are
not implemented and thus not included in DECRQSS responses. This is consistent
with the known limitations above.

---

**Last Updated**: 2026-08-18
