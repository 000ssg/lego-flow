# XTERM Terminal — Compliance

## xterm Control Sequences

The XTERM terminal implements the xterm(1) terminal emulator control sequences and extensions.

### All ANSI X3.64 Features

| Feature | Status |
|---------|--------|
| Standard CSI sequences | ✅ Inherited from ANSI |
| SGR 0–7, 30–47 | ✅ Inherited from ANSI |

### xterm SGR Extensions

| Code | Function | Status |
|------|----------|--------|
| 2 | Dim | ✅ Implemented (from VT100) |
| 9 | Strikethrough | ✅ Implemented (from VT100) |
| 53 | Overline | ✅ Implemented |
| 90–97 | Bright foreground | ✅ Implemented |
| 100–107 | Bright background | ✅ Implemented |
| 38;5;n | 256-color foreground | ✅ Implemented |
| 38;2;r;g;b | True color foreground | ✅ Implemented |
| 48;5;n | 256-color background | ✅ Implemented |
| 48;2;r;g;b | True color background | ✅ Implemented |
| 58;5;n | 256-color border | ✅ Not implemented |
| 58;2;r;g;b | True color border | ✅ Not implemented |

### DECSET Extensions

| Mode | Number | Function | Status |
|------|--------|----------|--------|
| 1000 | Mouse button event tracking | ✅ Implemented |
| 1001 | Hilite mouse tracking | ✅ Implemented |
| 1002 | Cell motion mouse tracking | ✅ Implemented |
| 1003 | All motion mouse tracking | ✅ Implemented |
| 1004 | Focus event tracking | ✅ Implemented |
| 1006 | SGR extended mouse | ✅ Implemented |
| 1015 | URXVT mouse mode | ✅ Implemented |
| 1016 | SGR mouse (alias) | ✅ Implemented |
| 2004 | Bracketed paste mode | ✅ Implemented |
| 2026 | Synchronized output (TMUX) | ✅ Implemented |

### OSC (Operating System Command)

| Code | Function | Status |
|------|----------|--------|
| 0 | Set icon and window title | ✅ Implemented |
| 1 | Set icon name | ✅ Implemented (same as 0) |
| 2 | Set window title | ✅ Implemented (same as 0) |
| 7 | Current working directory | ✅ Not implemented |
| 52 | Manipulate selection/clipboard | ✅ Implemented |

### DCS (Device Control String)

| Sequence | Function | Status |
|----------|----------|--------|
| DECRQSS | Request status string | ✅ Implemented |
| XTWINOP | Window manipulation | ✅ Not implemented |
| SCOSC | Set clipboard (OSC 52) | ✅ Implemented |

### Known Limitations

1. **No reverse video (SGR 52)** — inherited from VT200 not available in XTERM chain
2. **Mouse reports are stateful only** — no actual mouse event generation
3. **No window geometry queries** — XTWINOP not implemented
4. **No cursor blinking control** — DECSCUSR (CSI n q) not implemented
5. **No permit windowOps** — DECSET 1003/1010/1011 not implemented
6. **No debug mode** — DECSET 1010 not implemented
7. **No send escape sequence back** — DECSET 1011 not implemented
8. **No multimedia keys** — DECSET 1030+ not implemented
9. **No clipboard read** — only write (OSC 52) supported
