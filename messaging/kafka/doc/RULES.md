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

## 3. Version negotiation is mandatory

All 37 APIs are pinned to spec-max versions in `KafkaApiVersions.java`. The broker and client MUST use negotiated versions, not hardcoded v0.

- `KafkaAdminClient.getNegotiatedVersion(apiKey)` returns the cached negotiated version
- All admin methods use this method instead of hardcoded `(short) 0`
- Broker handlers accept and pass `version` parameter to codec encode/decode

## 4. Golden fixtures must match spec exactly

When creating or updating codecs:

1. **Write frozen order tables first** — `doc/spec/order/ApiName.Request.txt` and `doc/spec/order/ApiName.Response.txt`
2. **Write golden script second** — `doc/work/ApiName_golden.py` reference encoder
3. **Run golden script to generate vectors** — `python3 doc/work/ApiName_golden.py`
4. **Write codec third** — use generated golden vectors
5. **Write tests fourth** — use golden vectors as expected values

Never skip to code first. The frozen order tables are the source of truth.

## 5. Flexible encoding (KIP-482) boundaries

Flexible encoding applies at specific version boundaries per spec JSON:

- `flexible` field in spec JSON indicates which versions use flexible encoding
- Common boundaries: Metadata v9+, UpdateMetadata v6+, LeaderAndIsr v4+, Fetch v14+
- Flexible encoding uses: compact strings, N+1 varint counts, per-field endTags

## 6. endTags encoding

`endTags` is a varint count of tagged fields, NOT a 4-byte int:

- 0 = no tags = single 0x00 byte
- N = N tagged fields follow
- Each tagged field: tag (varint) + length (varint) + value

## 7. Documentation requirements

Per root AGENTS.md:

- **README.md** — must have shields, link to Architecture/Requirements/Compliance
- **doc/ARCHITECTURE.md** — current state, Mermaid diagrams, updated on architectural changes
- **doc/COMPLIANCE.md** — spec compliance matrix, updated with test counts
- **doc/REQUIREMENTS.md** — append-only, commit-based entries for all changes
- **doc/work/WIP.md** — work-in-progress tracking, matrix progress

All documentation must describe **current state only** — no historical/versioning references.
