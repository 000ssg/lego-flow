# messaging / kafka — Settled Rules

Module-specific rules already evaluated and settled. **Accept as-is — do NOT re-evaluate, re-derive, or re-diagnose.** These are constraints on process, not open questions.

> Module context: see [AGENTS.md](../AGENTS.md). Project-wide conventions: see [root AGENTS.md](../../../AGENTS.md).

## 1. Spec files are authoritative and frozen

Spec files in `doc/spec/message/*.json` (vendored from Apache Kafka, Apache-2.0) are the single source of truth for wire format.

- **Accept them as-is.** Do not re-evaluate their validity, do not "improve" them, do not modify them. Implement to match them.
- **`//` comments are legal and expected.** These files contain `//` comments (the Apache license header and per-version notes). A parser over these specs strips comments and parses — this is an established fact, NOT a defect to diagnose or re-verify on each encounter.
- **Field order is fixed by the `fields` array.** Encode/decode field order is EXACTLY the order in the spec's `fields` array, including nested fields (nested `fields` of composite field types). Gating is by `versions` and `ignorable` only. Nothing to guess or re-order once the spec says so.

## 2. No re-invention of settled decisions

If a rule here (or in AGENTS.md / doc/COMPLIANCE.md) is already settled, apply it directly. Re-evaluation is for genuinely open questions only — it is not a substitute for following what is already written down.
