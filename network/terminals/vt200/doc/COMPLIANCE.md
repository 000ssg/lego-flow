# VT200 Terminal — Compliance

## DEC VT200 Reference

The VT200 extends VT100 with mechanical terminal capabilities. The main
additions are SGR codes 52 (video reverse) and 55 (video normal).

### Extended SGR Codes

| Code | Attribute | Status |
|------|-----------|--------|
| 52 | Video Reverse | ✅ Implemented |
| 55 | Video Normal | ✅ Implemented |

### Inherited from VT100

All VT100 features are inherited: cursor motion, SGR 0-9/22-29/30-47/90-107,
DEC private modes, cursor save/restore, scroll regions, tab stops, device
attributes, and all CSI control sequences.

### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 | ✅ Inherited |

## Known Limitations

### SGR 52/55 — Display Rendering Integration

**Status**: Video reverse state is tracked (`isVideoReverse()`) but the actual
display rendering of reverse video depends on the display model's SGR reverse
attribute.

**Reason**: VT200 added SGR 52/55 as explicit video reverse/normal controls
beyond the standard SGR 7 (reverse). The implementation tracks the VT200
reverse state separately, but the display model already handles SGR 7.
The VT200 reverse mode is an alias for SGR 7 at the display level, so the
visual effect is the same. The separate tracking is maintained for protocol
compliance, not for visual differentiation.

### PF/PL Key Translation — Output Layer Responsibility

**Status**: Function key support is documented but key translation is output-
layer responsibility.

**Reason**: The VT200 has physical function keys (PF1–PF4, PL1–PL2) that
generate application-specific escape sequences. Key translation is the
responsibility of the input layer (keyboard driver, SSH channel, etc.),
not the terminal emulator. The emulator receives characters; it does not
generate key events. A VT200 terminal type name would tell the host what
key sequences the terminal produces — but that is an input configuration,
not an emulation feature.

### No Mechanical Terminal Emulation

**Status**: VT200 mechanical features (line feed variants, margin release)
are not fully simulated.

**Reason**: The VT200 was a hardware terminal with mechanical line-feed
select (CR/LF/both). In a software emulator, these are redundant because
the software controls exactly what characters appear on screen. The
"mechanical" features have no meaningful implementation in a purely
software display model. This is a known limitation that does not affect
practical usage.

---

**Last Updated**: 2026-08-18
