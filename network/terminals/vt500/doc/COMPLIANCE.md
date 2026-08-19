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
- Backtick–tilide: line drawing and special symbols
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

### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 (through VT400/VT200) | ✅ Inherited |

## Known Limitations

### DEC Special Mapping Uses Unicode Equivalents

**Status**: The DEC Special character set is mapped to Unicode rather than
actual DEC glyphs.

**Reason**: The original VT500 used a custom font for the line-drawing and
symbol characters. Modern systems display these via Unicode characters:
- Box-drawing: U+2500–U+257F (BOX DRAWINGS LIGHT)
- Symbol characters: mapped to corresponding Unicode symbols

This is the standard approach used by all modern terminal emulators (xterm,
GNOME Terminal, iTerm2) and provides better visual quality on modern displays.
The only practical difference is the exact glyph shape, which varies by font
but is semantically equivalent.

### Non-DEC-Special Charsets Use Identity Mapping

**Status**: UK, French, etc. charsets are tracked but don't transform characters.

**Reason**: The differences between international charsets are in the 0xA0–0xFF
range, which is outside the VT500's typical 7-bit ASCII input range (0x00–0x7F).
Full charset mapping would require:
1. A 128-entry lookup table per charset variant
2. Handling of 8-bit input (which requires Binary Mode negotiation)
3. Knowledge of the application's encoding preferences

Most telnet applications operate in 7-bit mode with US-ASCII, making the
international charset differences irrelevant. The charset is tracked for
protocol compliance (the correct state is reported via IS suboptions) but
the character data passes through unchanged.

### LINE DRAW Character Set

**Status**: Not a separate DEC charset; it's part of the DEC Special set.

**Reason**: The DEC Special character set (descriptor '0') includes both
line-drawing characters (in the 0x00–0x1F range) and symbol characters
(in the 0x41–0x5A and 0x61–0x7A ranges). There is no separate "LINE DRAW"
charset descriptor. Applications that need line drawing should use the
DEC Special charset, not look for a separate descriptor.

### Greek/Typography Charsets

**Status**: Not implemented.

**Reason**: DEC defined additional charsets for Greek (descriptor 'R' with
Greek letters in some implementations) and typographic variants, but these
were experimental and never standardized in any RFC. Implementing them would
add complexity without RFC support. Greek text is properly handled by
UTF-8/Unicode in modern terminal environments.

### User-Defined Charsets Are Single-Character Mappings Only

**Status**: Full charset definition via DCS is not supported.

**Reason**: The DEC protocol allows defining custom character mappings via
DCS (Device Control String) sequences, but the VT500 implementation only
supports single-character replacements (one ASCII code → one display character).
Full charset definition (replacing an entire 128-character table) would
require a significant DCS parser extension. This is acceptable because:
1. Most applications use standard charsets
2. Single-character replacement covers the common case of defining a
   custom line-drawing or symbol character
3. Full DCS charset definition is rarely used in practice

### Multiple Character Set (MCS)

**Status**: Not implemented (DEC MCS allows defining custom charsets at
arbitrary G-positions).

**Reason**: MCS is a complex extension that allows multiple custom charset
definitions at different G-positions (G2, G3, G4). The VT500 only supports
G0/G1. MCS was never standardized in an RFC and had limited adoption.
Modern UTF-8 terminals have largely replaced the concept of multiple
character sets with Unicode.

### G2/G3 Character Sets

**Status**: Not implemented (VT500 only uses G0/G1).

**Reason**: The VT500 specification only defines G0 and G1 character sets.
G2 and G3 are extensions used by some terminal emulator software (not the
VT500 hardware). These would require extending the charset selector to
handle additional descriptors beyond what the VT500 defines.

### DECRQSS for User-Defined Charset

**Status**: Not implemented.

**Reason**: DECRQSS (Request Status String) is a generic query mechanism.
VT500 supports DECRQSS for DEC private modes but not for user-defined
charset queries. This is because:
1. User-defined charset data is stored in the terminal state, not reported
   by the terminal in a standardized response format
2. DECRQSS was primarily designed for reporting terminal capabilities,
   not runtime configuration data
3. Applications can query charset state through the proper IS suboption
   mechanism

---

**Last Updated**: 2026-08-18
