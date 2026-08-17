# VT500 Terminal — Compliance

## DEC VT500 Reference

The VT500 extends VT400 with advanced character set support including
G0/G1 charset selection via ESC paren/desc and SO/SI switching.

### DEC Character Sets

| Descriptor | Name | Status |
|------------|------|--------|
| B | ASCII (default) | ✅ Implemented |
| 0 | DEC Special / Line Drawing | ✅ Implemented (Unicode mapping) |
| U | UK | ✅ Implemented (identity mapping) |
| K | French | ✅ Implemented (identity mapping) |
| W | French-Canadian | ✅ Implemented (identity mapping) |
| R | International | ✅ Implemented (identity mapping) |
| Q | Scandinavian | ✅ Implemented (identity mapping) |
| Y | German | ✅ Implemented (identity mapping) |
| null | User-Defined | ✅ Implemented (via DCS, single-char) |

### Character Set Selection

| Sequence | Function | Status |
|----------|----------|--------|
| ESC ( n | DECSCE — select G0 charset | ✅ Implemented |
| ESC ) n | DECSCE — select G1 charset | ✅ Implemented |
| SO (0x0E) | SSO — activate G0 | ✅ Implemented |
| SI (0x0F) | SSI — activate G1 | ✅ Implemented |
| DCS Pt ST | User-defined charset (single-char) | ✅ Implemented |

### DEC Special Character Mapping

The DEC Special charset maps ASCII positions to Unicode equivalents:
- Backtick–tilde: line drawing and special symbols
- A–Z: Unicode symbols (diamonds, circles, box drawing, etc.)
- a–z: More Unicode symbols

Full mapping table: see `VT500Terminal.DEC_SPECIAL_MAP` constant.

### DCS Support

| DCS | Function | Status |
|-----|----------|--------|
| DCS \| Ps ; Pt ST | User-defined character mapping | ✅ Implemented |
| DCS $q Pt ST | DECRQSS (request status string) | ❌ Not implemented |

### OSC Support

| OSC | Function | Status |
|-----|----------|--------|
| 10–19 | Window host commands | ✅ Recognized (no action) |
| 14;RRGGBB | Background color (inherited) | ✅ Implemented |

### Inherited from VT400/VT200/VT100

All features from the parent chain are inherited.

#
### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through VT400/VT200) | ✅ Inherited |
# Known Limitations

1. **DEC Special mapping uses Unicode equivalents** — The DEC Special character
   set is mapped to Unicode rather than actual DEC glyphs. Most modern displays
   render these correctly.
2. **Non-DEC-Special charsets use identity mapping** — UK, French, etc. charsets
   are tracked but don't transform characters (most differences are in the
   0xA0–0xFF range which are outside the VT500's typical ASCII input).
   This is a known limitation; full charset mapping would require a lookup
   table for each charset variant.
3. **LINE DRAW character set** — Not a separate DEC charset; it's part of
   the DEC Special set.
4. **Greek/Typography charsets** — Not implemented
5. **User-defined charsets are single-character mappings only** — Full charset
   definition via DCS is not supported
6. **Multiple Character Set (MCS)** — Not implemented (DEC MCS allows
   defining custom charsets at arbitrary G-positions)
7. **G2/G3 character sets** — Not implemented (VT500 only uses G0/G1)
8. **DECRQSS for user-defined charset** — Not implemented

## Verification

| Feature | Test Verification |
|---------|-----------------|
| Charset selection (ESC /) | `VT500TerminalTest.testCharsetSelection` |
| SO/SI switching | `VT500TerminalTest.testSOSwitching` |
| DEC Special mapping | `VT500TerminalTest.testDecSpecialMapping` |
| DCS user-defined charset | `VT500TerminalTest.testDcsCharset` |
| Inherited features | All parent tests pass |
| DCS user-defined charset | `VT500TerminalTest.testDcsUserDefinedCharset` |
| Reset clears charset state | `VT500TerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
