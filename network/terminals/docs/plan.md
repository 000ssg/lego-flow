# Terminal Emulation Framework — Implementation Plan

## Overview

This document outlines the terminal emulation framework and Telnet protocol implementation for Lego Flow. The framework provides reusable terminal emulator modules compatible with VT52, VT100, VT200, VT400, VT500, ANSI X3.64, and XTERM terminal types, along with a Telnet protocol bridge (RFC 854).

---

## Architecture

### Module Hierarchy

```mermaid
graph TD
    TB["terminals-base<br/>Core abstractions: Terminal, DisplayModel, Screen,<br/>Cursor, TermAttr, EscapeParser, Config"]

    V52["vt52<br/>VT52 — ESC+letter commands,<br/>cursor address (ESC Y)"]
    V100["vt100<br/>VT100 — full DEC, CSI, SGR<br/>0-7, DEC private modes"]

    V200["vt200<br/>VT200 — extends VT100,<br/>SGR 52/55, PF keys"]
    V400["vt400<br/>VT400 — extends VT200,<br/>2-window, extended SGR, DECCOM"]
    V500["vt500<br/>VT500 — extends VT400,<br/>DEC charsets, DCS, SO/SI"]

    ANS["ansi<br/>ANSI X3.64 — extends VT100,<br/>no DEC private modes"]
    XT["xterm<br/>XTERM — extends ANSI,<br/>256/true color, mouse,<br/>bracketed paste, sync"]

    TBN["telnet-base<br/>RFC 854 parser, connection,<br/>IAC escaping"]
    TN["telnet-negotiation<br/>RFC 855 option negotiation,<br/>TTYPE, NAWS, Speed"]
    TG["telnet-gateway<br/>Protocol ↔ terminal bridge,<br/>ECHO, TTYPE, NAWS"]

    TB --> V52
    TB --> V100
    V100 --> V200
    V200 --> V400
    V400 --> V500
    V100 --> ANS
    ANS --> XT
    TBN --> TN
    TBN --> TG
    TN --> TG
    TB --> TG
```

### Inheritance Chains

| Chain | Modules | Key Capabilities |
|-------|---------|-----------------|
| **VT lineage** | terminals-base → vt100 → vt200 → vt400 → vt500 | DEC private modes, cursor save/restore, SGR, scroll regions, charsets |
| **ANSI lineage** | terminals-base → vt100 → ansi → xterm | Standard CSI/SGR, 256/true color, mouse tracking, bracketed paste |
| **Standalone** | terminals-base → vt52 | ESC+letter commands, VT52 cursor addressing |

---

## Terminal Type Reference

### VT52
- **Protocol**: ESC followed by single letter commands
- **Cursor Addressing**: ESC Y row col (value + 32 encoding)
- **Color**: No
- **Key Features**: Cursor motion (I/F/S/R), clear display (J), clear EOL (E), line feed (D)
- **Standards**: DEC VT52 terminal manual
- **Use Cases**: BBS systems, vintage terminal emulation, minimal footprint

### VT100
- **Protocol**: CSI sequences with final byte dispatch
- **Cursor Motion**: CUU/CUD/CUF/CUB/CUP/CHA/VPA/CNL/CPL
- **SGR**: Codes 0–9 (bold, dim, italic, underline, blink, reverse, hidden, strikethrough)
- **Colors**: 8-color foreground/background (30–37, 40–47) + bright (90–97, 100–107)
- **DEC Private**: DECSET/DECRST for origin mode, auto-wrap, application keypad
- **Key Features**: Cursor save/restore, scroll regions, line/char insert/delete, erase display/line
- **Standards**: DEC VT100 terminal manual, ANSI X3.64 base
- **Use Cases**: Classic terminal emulation, SSH, Telnet servers

### VT200
- **Extends**: VT100
- **Additional SGR**: Code 52 (video reverse), 55 (video normal)
- **Key Features**: Function key support (PF1–PF3, PL1–PL6)
- **Use Cases**: Mechanical VT200/VT220 compatibility

### VT400
- **Extends**: VT200
- **Extended SGR**: Codes 82–89 (extended foreground), 92–99 (extended background)
- **Key Features**: 2-window support, scroll history, DECCOM commodity codes, insert/delete column
- **Use Cases**: DEC VT400/VT420 workstation emulation

### VT500
- **Extends**: VT400
- **DEC Character Sets**: G0/G1 charset selection via SO/SI, DECSET-based
- **Character Sets**: ASCII, DEC Special, UK, French, French-Canadian, International, Scandinavian, German, User-Defined
- **DCS**: User-defined character set definition
- **Use Cases**: DEC VT500/VT520 advanced workstation, international character sets

### ANSI X3.64
- **Extends**: VT100 (minus DEC private modes)
- **Protocol**: Standard CSI sequences only, no ESC [ ? prefix
- **SGR**: Standard codes 0–9, 30–47
- **Key Features**: ANSI-compliant behavior, ignores DEC extensions
- **Standards**: ANSI X3.64 (1979), ECMA-48 base
- **Use Cases**: Strict ANSI compliance, cross-platform compatibility

### XTERM
- **Extends**: ANSI
- **Color**: 256-color palette (38;5;n / 48;5;n), true RGB (38;2;r;g;b / 48;2;r;g;b)
- **Mouse Tracking**: Button event (1000), highlight (1002), all motion (1003), SGR extended (1006)
- **Modern Features**: Bracketed paste (2024), synchronized output (2026), focus tracking (1004)
- **Text Decoration**: Underline styles (4:0–4:5), overline (53)
- **DCS**: DECRQSS status request strings
- **Key Features**: Most feature-rich terminal type, modern terminal emulator standard
- **Standards**: xterm control sequences, de facto terminal standard
- **Use Cases**: Modern SSH, web terminals, IDE integrated terminals

---

## Telnet Protocol

### Module Structure

| Module | Purpose | Standards |
|--------|---------|-----------|
| telnet-base | RFC 854 state machine, parser, connection, IAC escaping | RFC 854 |
| telnet-negotiation | Option negotiation state machine, TTYPE, NAWS, Speed handlers | RFC 855, RFC 1091, RFC 1073, RFC 1079 |
| telnet-gateway | Bridge between Telnet connection and terminal emulator | RFC 854 + terminal protocol mapping |

### Telnet Gateway

The gateway bridges raw Telnet protocol with terminal emulation:
- **Inbound**: Parses Telnet from peer → strips IAC commands → feeds clean data to terminal
- **Outbound**: Renders terminal output → sends with IAC escaping
- **Option Negotiation**: ECHO (default on), SUPPRESS_GO_AHEAD (default on), TTYPE (responds with terminal type), NAWS (dimension updates)

---

## Reuse Model

### SSH Protocol Integration
- Terminal types selected via SSH terminal-type negotiation (TERM environment variable)
- Gateway replaced by SSH channel handler
- Terminal rendering feeds into SSH pseudo-terminal

### CLI Integration
- TerminalConfig used for console output formatting
- KeyTranslator converts raw input to escape sequences

### Swing/Web Applications
- Terminal interface with event listeners drives UI rendering
- Screen buffer rendered to Swing components or HTML/Canvas
- Cursor position and attributes mapped to UI properties

### General Pattern
```java
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).colorDepth(256).build();
Terminal terminal = TerminalFactory.create("xterm", config);
terminal.addEventListener(renderer);
terminal.feed(incomingBytes);
```

---

## Compatibility Matrix

| Feature | VT52 | VT100 | VT200 | VT400 | VT500 | ANSI | XTERM |
|---------|------|-------|-------|-------|-------|------|-------|
| CSI cursor motion | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SGR basic (0-9) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 8-color (30-47) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| DEC private modes | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Cursor save/restore | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scroll regions | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 256-color | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| True color RGB | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Mouse tracking | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Bracketed paste | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| DEC charsets | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Synchronized output | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## Testing Strategy

| Module | Unit Tests | Demo Tests | Coverage Target |
|--------|-----------|------------|-----------------|
| terminals-base | 165 | TBD | ≥ 80% |
| vt52 | 15 | TBD | ≥ 80% |
| vt100 | 30 | TBD | ≥ 80% |
| vt200 | 6 | TBD | ≥ 90% |
| vt400 | 6 | TBD | ≥ 80% |
| vt500 | 6 | TBD | ≥ 80% |
| ansi | 6 | TBD | ≥ 90% |
| xterm | 25 | TBD | ≥ 80% |
| telnet-base | 60 | TBD | ≥ 90% |
| telnet-negotiation | 24 | TBD | ≥ 80% |
| telnet-gateway | 9 | TBD | ≥ 80% |

**Total tests**: 352 unit tests + demos

### Test Coverage Gaps (to be addressed)
- vt200, vt400, vt500, ansi — need more comprehensive tests
- telnet-gateway — needs integration test scenarios
- All modules — need demo test classes

---

## Pending Tasks

### Documentation
- [x] ARCHITECTURE.md — all modules (Mermaid diagrams ✅)
- [x] REQUIREMENTS.md — all modules
- [ ] Cost Estimate sections — all 11 modules
- [ ] COMPLIANCE.md — telnet-gateway

### Demos
- [ ] Demo classes for all 11 modules (src/main/demo + src/test/demo)

### Quality
- [ ] Test coverage audit and gap remediation
- [ ] Final build verification

---

**Last Updated**: 2026-08-17
