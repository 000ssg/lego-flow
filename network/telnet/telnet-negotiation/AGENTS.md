# network / telnet / negotiation — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

Telnet option negotiation per RFC 855. Implements the 4-state machine (OFF → OFF_DEF → ON_DEF → ON) for each option, plus handlers for TTYPE (RFC 1091), NAWS (RFC 1073), and Speed (RFC 1079).

## Key Classes

- `OptionState` — enum: OFF, OFF_DEF, ON_DEF, ON
- `OptionRecord` — local + remote state per option
- `OptionNegotiator` — manages all options, default-accepts everything
- `TTYPEHandler` — terminal type exchange (IS=0, SEND=1)
- `NAWSHandler` — window size exchange (cols/rows, big-endian)
- `SpeedHandler` — terminal speed exchange (BPS string, IS=0, SEND=1)

## OptionState 4-State Machine

```
OFF → OFF_DEF (tentative OFF → ON) → ON_DEF (tentative ON → OFF) → ON
```

- OFF: option is off, no negotiation pending
- OFF_DEF: option is off, deferred ON request pending
- ON_DEF: option is on, deferred OFF request pending
- ON: option is on, no negotiation pending

## Testing

- Tests: ~21
- Test state transitions
- Test negotiator default behavior
- Test TTYPE, NAWS, Speed handlers

Total tests: ~21
