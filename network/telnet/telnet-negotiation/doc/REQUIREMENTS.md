# Telnet Negotiation — Requirements

## Requirements

### RFC 855 State Machine
1. 4 states: OFF, OFF_DEF, ON_DEF, ON
2. Per-option local and remote state tracking
3. State transitions on WILL/WONT/DO/DONT
4. OptionNegotiator with override hooks

### RFC 1091 TTYPE
1. IS suboption (0) — send local terminal type
2. SEND suboption (1) — request remote terminal type
3. Null-terminated type string
4. Callback on remote type received

### RFC 1073 NAWS
1. 4-byte payload: cols (2 bytes BE), rows (2 bytes BE)
2. Update local dimensions on remote resize
3. Callback on remote size received

### RFC 1079 Speed
1. IS suboption (0) — send local speed as decimal string
2. SEND suboption (1) — request remote speed
3. Callback on remote speed received

## Test Coverage
- OptionNegotiatorTest — state transitions, default behavior
- TTYPEHandlerTest — IS, SEND, local type, remote type callback
- NAWSHandlerTest — local size, remote size parsing
- SpeedHandlerTest — IS, SEND, local speed, remote speed callback
- Total tests: ~21

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (terminal-framework agent) |
| Agent tokens | ~40K |
| Agent tool calls | ~30 |
| Agent wall time | ~15 min |
| Files created/modified | 7 |
| Lines added/removed | +400 / -0 |
| Tests added | 24 (total: 24) |

---

**Last Updated**: 2026-08-17
