# XTERM Terminal — Requirements

## Requirements

### XTERM Extensions
1. 256-color foreground/background (38;5;n, 48;5;n)
2. True color RGB foreground/background (38;2;r;g;b, 48;2;r;g;b)
3. Mouse tracking: normal (1000), highlight (1002), cell motion (1003)
4. SGR extended mouse (1006), URXVT mouse (1015)
5. Bracketed paste mode (2024)
6. Synchronized output mode (2026)
7. Focus event tracking (1004)
8. Underline styles (4:0-4:5)
9. Overline (SGR 53/55)
10. DCS handling for DECRQSS
11. All ANSI/VT100 features inherited

## Test Coverage
- Total tests: 25
