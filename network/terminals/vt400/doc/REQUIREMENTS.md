# VT400 Terminal — Requirements

## Requirements

### VT400 Extensions
1. Extended SGR: 82-89 (extended fg), 92-99 (extended bg)
2. Window selection: CSI n t (1 or 2)
3. All VT200 features inherited

## Test Coverage
- Total tests: 6

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (terminal-framework agent) |
| Agent tokens | ~25K |
| Agent tool calls | ~18 |
| Agent wall time | ~10 min |
| Files created/modified | 5 |
| Lines added/removed | +180 / -0 |
| Tests added | 6 (total: 6) |

---

**Last Updated**: 2026-08-17
