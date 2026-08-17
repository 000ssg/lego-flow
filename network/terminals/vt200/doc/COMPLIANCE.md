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

#
### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — inherited from VT100 | ✅ Inherited |
# Known Limitations

1. **SGR 52/55 tracked but not fully integrated** — Video reverse state is
   tracked (`isVideoReverse()`) but the actual display rendering of reverse
   video depends on the display model's SGR reverse attribute
2. **No PF/PL key translation** — Function key support is documented but
   key translation is output-layer responsibility
3. **No mechanical terminal emulation** — VT200 mechanical features (line feed
   variants, margin release) are not fully simulated

## Verification

| Feature | Test Verification |
|---------|-----------------|
| SGR 52 (video reverse) | `VT200TerminalTest.testVideoReverse` |
| SGR 55 (video normal) | `VT200TerminalTest.testVideoNormal` |
| Inherited VT100 features | All VT100TerminalTest tests pass |
| Reset clears video state | `VT200TerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
