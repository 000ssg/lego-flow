# VT100 Terminal — Requirements

## Requirements

### VT100 Protocol
1. CSI cursor motion: CUU, CUD, CUF, CUB, CUP, HVP, CHA, VPA, CNL, CPL
2. CSI erase: ED (J), EL (K), ECH (X)
3. CSI insert/delete: IL (L), DL (M), ICH (@), DCH (P)
4. SGR text attributes (0-9, 22-29, 30-49, 90-107)
5. DECSET/DECRST for DEC private modes (?h, ?l)
6. Cursor save/restore (CSI 7/8, CSI s/u)
7. Scroll region (CSI r)
8. Repeat preceding character (CSI b)
9. Device attributes (CSI ?c)
10. OSC title setting (OSC 0, 1, 2)

### Terminal Interface
1. Implement Terminal interface via AbstractTerminal
2. Return type "vt100"
3. supportsColor() returns true
4. Protected constructor for extension

## Test Coverage

- VT100TerminalTest — cursor motion, SGR, DEC modes, scroll region, save/restore
- Total tests: 30
