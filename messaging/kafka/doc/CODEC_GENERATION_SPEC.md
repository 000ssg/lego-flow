# Kafka Codec Code-Generation Spec (trusted, single source of truth)

Status: **normative** for phase 6a step S1–S6. This document + `doc/spec/message/*.json`
(apache/kafka 3.6.1) are the *single source of truth*. `gen_kafka.py` is the compiler;
every generated line must be re-derivable from this file and the JSON spec. No step may
re-open a rule settled here — if a rule conflicts with reality, fix the rule here, then
regenerate.

## 1. Scope and pipeline

```
doc/spec/message/<Model>.json  ──►  gen_kafka.py  ──►  3 generated families
  (JSON5, Kafka 3.6.1)                (CLI below)      protocol/<Model>.java    (data records)
                                                  codec/<Model>Codec.java       (version-grouped codec)
                                                  test/.../<Model>CodecTest.java (per-group tests)
```

- Generator: `messaging/kafka/gen_kafka.py`, Python 3.12, stdlib only (JSON5 comments
  stripped line-wise — no `//` in values).
- CLI commands:
  - `python3 gen_kafka.py apis` — list of the 37 APIs with apiKey / specMax / flexFrom / implMax / specMax+1.
  - `python3 gen_kafka.py data <Model>` — data records + builder + compat constructors.
  - `python3 gen_kafka.py builder <Model>` — builder only (subset of `data`).
  - `python3 gen_kafka.py codec <Model>` — `<Model>Codec.java` (S3, this session).
  - `python3 gen_kafka.py tests <Model>` — `<Model>CodecTest.java` (S4).
  - `python3 gen_kafka.py matrix` — one row per (API × direction × version) matching the
    table format in `doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md` § "Coverage".
  - `python3 gen_kafka.py order <Api> <Request|Response> [vLo] [vHi]` — field processing
    order (§3.1): layout runs (equal run = byte-identical layout) + per-version field lines.
  - `python3 gen_kafka.py freeze <Api> <Request|Response>` — write the binding order table
    to `doc/spec/order/<Api>.<Kind>.txt`; refuses to overwrite a differing table.
- A *step* = one of: emit family X for one API, regenerate, run gates, commit (gate G6).
  Steps are compaction-resistant: §13 protocol, step docs S1–S6 in
  `doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md`.

### 1.1 The 85 KB monolith

`KafkaCodec.java` (façade) currently still contains the pre-split inline bodies of most
APIs (32 of 37) plus thin delegations for the 5 carved-out codecs (ApiVersions, Fetch,
Produce, SaslAuthenticate, SaslHandshake). Carving rule: a dedicated `<Model>Codec`
replaces the inline body; the façade keeps a thin delegate **at `PINNED_VERSION`** only.
The façade never owns version logic.

## 2. Type mapping (corrected)

Spec types actually present in `doc/spec/message/`: `int8 int16 int32 int64 uint8
bool string bytes uuid []<T> {Entity}`.

> **Corrections to earlier session notes** (verified against `KafkaCodecPrimitives`):
> there is **no** `writeBool`/`readBool` primitive — bool is `buf.put((byte)...)` /
> `buf.get() != 0`. There is **no** `writeNullableBytes`/`readNullableBytes` — nullable
> bytes use `writeBytesField`/`readBytesField` (legacy, -1 length = null) and
> `writeCompactBytes`/`readCompactBytes` (flexible, count 0 = null).
> `uint8` → Java `short` (never `byte`).

| Spec type | Java type | legacy write | legacy read | flexible write | flexible read |
|---|---|---|---|---|---|
| int8 | `byte` | `buf.put(x)` | `buf.get()` | same | same |
| int16 | `short` | `buf.putShort(x)` | `buf.getShort()` | same | same |
| int32 | `int` | `buf.putInt(x)` | `buf.getInt()` | same | same |
| int64 | `long` | `buf.putLong(x)` | `buf.getLong()` | same | same |
| uint8 | `short` | `buf.putShort(x)` | `(short) buf.get()`* | `KafkaCodecPrimitives.writeVarint(buf, x)` | `(short) KafkaCodecPrimitives.readVarint(buf)`* |
| bool | `boolean` | `buf.put((byte) (x ? 1 : 0))` | `buf.get() != 0` | `KafkaCodecPrimitives.writeVarint(buf, (int) (x ? 1 : 0))` | `KafkaCodecPrimitives.readVarint(buf) != 0` |
| string | `String` | `KafkaCodecPrimitives.writeString(buf, x)` | `KafkaCodecPrimitives.readString(buf)` | `KafkaCodecPrimitives.writeCompactString(buf, x)` | `KafkaCodecPrimitives.readCompactString(buf)` |
| bytes | `byte[]` | `KafkaCodecPrimitives.writeBytesField(buf, x)` | `KafkaCodecPrimitives.readBytesField(buf)` | `KafkaCodecPrimitives.writeCompactBytes(buf, x)` | `KafkaCodecPrimitives.readCompactBytes(buf)` |
| records | `byte[]` | opaque: `writeBytesField` | `readBytesField` | opaque: `writeCompactBytes` | `readCompactBytes` |
| uuid | `byte[16]` | `KafkaCodecPrimitives.writeUuid(buf, x)` | `KafkaCodecPrimitives.readUuid(buf)` | same | same |
| `[]T` | `List<T>` | count `buf.putInt(n)` (null array = -1) | `int n = buf.getInt(); ...` | count `writeVarint(buf, n + 1)` (null array = 0) | `int n = KafkaCodecPrimitives.readVarint(buf) - 1; ...` |
| `{Entity}` | nested record `<Parent>.<Entity>` | inline fields | inline fields | inline fields + **tagged trailer** | inline fields + `nextTag` skip |

\* uint8 zigzag: `KafkaCodecPrimitives`' varint is the standard KIP-482 varint; the hand
codecs use `putShort` for uint8 in legacy positions (int8 position). The generator emits
`putShort`/`(short) buf.get()` for uint8 in legacy and `writeVarint`/`readVarint` cast to
`short` in flexible positions — identical to the hand-written Fetch/Produce flexible code.

**Nullability**
- Legacy nullable string/bytes: null → length -1 (the nullable-primitive pair).
- Flexible **non**-nullable string/bytes: never null; generator asserts non-null encode,
  decodes non-null.
- Flexible nullable string/bytes: null → length 0 / 1 byte respectively (primitive contract).
- **mapKey strings** (array-keyed by name, e.g. Produce topics): flexible non-nullable
  compact string; nullable everywhere.
- Compact arrays of nullable elements (KIP-354 style) do not occur in the 3.6.1 message
  specs used here; if one appears, element count = n + number-of-nulls with varint per
  element — handled by a dedicated emitter (none triggered by current spec).

**Request header (written by the client, NOT by the body codec)**
- legacy (version < flexFrom): `putShort(apiKey | 0x8000), putInt(correlationId),
  putShort(clientIdLen), ...`
- flexible: `putShort(apiKey | 0x8000), writeVarintSigned(correlationId),
  writeCompactString(clientId)` — the `0x8000` tag signals the flexible header.
- The generated body codec encodes **only the body**; the header is the caller's concern
  (KafkaClient), matching the hand-written codecs.

## 3. Version rules

- Version ranges parse `"0+"`, `"1-8"`, `"8-10"`, `"3-8"`, `"none"`; `validVersions`
  `"0-12"` and `"0+"` (open-ended; specMax = last recorded).
- `fieldAt(f, v)` = `parseRange(f.versions).contains(v)` when `versions` is present, else
  true.
- **Layout(v)** = ordered list of (field, presence, flexible-flags) for version v.
- **Groups** = maximal consecutive version ranges with identical Layout, per direction.
  Emit one `encodeRequestV<m>`/`decodeRequestV<m>` (m = group low) per group.
- **Fall-through**: versions inside a group decode byte-identically; the generator emits
  a `// fall-through: v<m>..v<H> share the v<m> layout` comment on the dispatch.
- **flexFrom** = first flexible version (`flexibleVersions` `"9+"` → 9; `"none"` → ∞).
  Every version ≥ flexFrom uses flexible encoding per §2.
- `SPEC_MAX_VERSION` = spec max; `PINNED_VERSION` = in-house pin (initially = spec max).
  A codec implements every version 0..SPEC_MAX; versions > SPEC_MAX throw
  `CodecNotImplementedException`.

### 3.1 Field processing order (binding)

The wire order of a struct's fields at version v is a **pure function of the spec
JSON** — no inference, no human re-derivation:

1. **Fixed section** — fields in `fields[]` array order, filtered by
   `fieldAt(f, v)`.
2. **Trailing tagged section** — fields with a `taggedVersions` set (present at v)
   appended after the fixed section, sorted by **ascending `tag`**.
3. **Recursive per struct level** — each nested struct object/array follows the same
   rule independently.
4. **Flex state is part of the layout signature** — the flexible-encoding cutover
   (v ≥ flexFrom) changes every compact-string / bytes / array prefix, so a flex-only
   change must break a byte-identity run even when the field list is unchanged.

`gen_kafka.py` enforces this: `order <Api> <Kind> [vLo vHi] [--lines]` prints the
layout runs (equal run = byte-identical field layout, i.e. fall-through candidate)
and the per-version field lines; `freeze <Api> <Kind>` writes the binding table to
`doc/spec/order/<Api>.<Kind>.txt` and **refuses to overwrite a differing table** —
any order change is a spec amendment requiring review, never a silent generator
rewrite. The frozen tables are the review artifact every codec commit cross-checks
against (see the §12 CLI: `order` / `freeze`).

- **Tagged trailer rule** (flexible only): every struct and the top-level message ends
  with `writeVarint(buf, 0)`; decode runs `int c = readVarint(buf); while (c >= 0) {
  nextTag(reader); c = readVarint(buf); }`. Nested structs each get their own trailer —
  verified against FetchCodec v7+ and ProduceCodec v9.

## 4. Absent-field defaults (decode of a version-V wire against the max-version record)

For each field f of the max-version record not present at v:
1. f has a spec `default` → use it (`default: null` → `null`; numeric → literal; string →
   quoted).
2. else f `ignorable: true` → `null` (string/bytes/uuid/array/object) or spec-type zero
   (primitives: 0 / false / empty list).
3. else → `null` (object/array/list) or spec-type zero (primitive).
   - List fields: absent → `List.of()` **only if ignorable or defaulted**; otherwise
     `null` (flexible null-array decode yields `null`, not `List.of()` — keeps
     round-trip equality true).

Canonical-constructor argument order = **spec field order** at max version.

## 5. Data-class rules (gen: `data <Model>`)

Emit `protocol/<Model>.java`:
- Canonical record: **max-version fields, spec order**; nested struct → nested record
  named exactly as the spec `entityType`; `[]T` → `List<T>`.
- Javadoc: `@param` per component + `@since 0.1.0` + spec provenance
  (`generated by gen_kafka.py from doc/spec/message/<Model>.json, Kafka 3.6.1`).
- **Compatibility constructors**: one per distinct older-version signature with at least
  one existing repo call site (audit `grep -rn "new <Model>("`); body delegates to
  canonical with §4 defaults. The FetchResponse pattern (canonical + compat + builder) is
  the model.
- **Convenience constructors** (hand-written, e.g. `MetadataRequest()`,
  `MetadataRequest(List<String>)`): preserved verbatim by the generator when present in
  the current file (mechanism: existing file's secondary constructors are re-emitted
  unchanged).
- Builder (hybrid): `public static Builder builder()`; per-field setters; field
  initializers = §4 absent defaults (spec default where present, else type zero / null /
  `List.of()` for lists); `build()` → canonical constructor. No per-version validation.
- Generated data classes are **stamped** with a header comment `// GENERATED: do not
  edit — regenerate: python3 gen_kafka.py data <Model>`; hand-written modules keep the
  no-stamp convention until regenerated (S2+).

## 6. Codec rules (gen: `codec <Model>`)

Emit `codec/<Model>Codec.java`:

```
public final class <Model>Codec {
    public static final short SPEC_MAX_VERSION = <specMax>;   // Kafka 3.6.1 spec
    public static final short PINNED_VERSION = <pinned>;      // in-house pin

    public static byte[] encodeRequest(short version, <Model> req) { switch… }
    public static <Model> decodeRequest(ByteBuffer buf, short version) { switch… }
    public static byte[] encodeResponse(short version, <Model>Response resp) { … }
    public static <Model>Response decodeResponse(ByteBuffer buf, short version) { … }
    private static … encodeRequestV<m>(…)   // one per group per direction
    private static … decodeRequestV<m>(…)
    … response group methods …
}
```

- Dispatch: `switch (version) { case <low>.. <high>: return encodeRequestV<m>(req);
  … default: throw new CodecNotImplementedException("<Model> request v" + version); }`.
- Encode method body: size estimate + single-pass write (BufferPool); estimate rules:
  - int8 1 / int16 2 / uint8 2 / int32 4 / int64 8 / bool 1 / uuid 16
  - legacy string: `2 + (x == null ? 0 : x.getBytes(UTF_8).length)`; legacy bytes:
    `4 + (x == null ? 0 : x.length)`
  - flexible string/bytes: `varintSize(len + 1) + len` (len = 0 when null); nullable
    adds nothing extra (count 0 encodes null)
  - legacy array count: `+4` (or -1 for null); flexible array count:
    `+varintSize(n + 1)` (or 1 byte for null → 0)
  - nested struct: recurse.
  - The estimate may over-allocate (documented); correctness = wire bytes, not tightness.
- Decode: fields in spec order; trailing tagged-skip per §3; absent fields per §4.
- **No `var` in generated code** (house style from ProduceCodec: explicit types);
  `ArrayList` + final `List.copyOf` only where a `List` is stored in a mutable-then-final
  context — hand style is raw `ArrayList`; generator emits `ArrayList` (house style).
- Static imports: `import static ssg.legoflow.messaging.kafka.codec.KafkaCodecPrimitives.*;`
  plus `java.nio.*`, `java.nio.charset.StandardCharsets`, `java.util.*`.

## 7. Façade rules

For each carved-out `<Model>Codec`, the façade becomes:

```java
/** <Model> (key <apiKey>): delegates to {@link <Model>Codec} at the in-house pinned version (v<pinned>). */
public static byte[] encode<Model>Request(<Model>Request req) {
    return <Model>Codec.encodeRequest(<Model>Codec.PINNED_VERSION, req);
}
public static <Model>Request decode<Model>Request(ByteBuffer buf) {
    return <Model>Codec.decodeRequest(buf, <Model>Codec.PINNED_VERSION);
}
```

and the inline body is **deleted**. The generator extracts existing delegations
automatically (regex on `return <X>Codec.encodeRequest(<X>Codec.PINNED_VERSION`); a step
must not re-add an inline body.

## 8. Test rules (gen: `tests <Model>`)

Emit `src/test/java/.../codec/<Model>CodecTest.java`:

```
class <Model>CodecTest {
  static final <Model>Request REQ = <sample>;        // non-trivial values, see §8.2
  static final <Model>Response RESP = <sample>;

  @Nested request {
    per group G (lo..hi):
      @DisplayName("request round-trip v<lo> (layout v<lo>..v<hi>)")
      void v<lo>(): encode(v<lo>) → decode → assertEquals(REQ, …); field-by-field asserts;
                    second encode byte-identical (assertArrayEquals)
      for each v in (lo+1..hi): byte-identity fall-through:
        assertArrayEquals(encode(v<lo>), encode(v))
    if SPEC_MAX < 12: @Test v<SPEC_MAX+1> → assertThrows(CodecNotImplementedException)
  }
  @Nested response { symmetric }
}
```

- §8.2 samples: strings non-empty and distinct; lists of 2 elements with distinct values
  when the type is a nested record; nullable fields set **and** null in one extra test
  per nullable field pair; primitives non-zero.
- Asserts: whole-object `assertEquals` (record equality) plus per-field asserts on every
  component (round-trip must be field-exact, matching ProduceCodecTest style).
- Fall-through byte-identity uses the same sample object.
- No sleep / latch / network. Pure codec.

## 9. Gates (per step)

- **G1 spec-first**: the step doc block lists exactly: files in, files out, generator
  command(s), expected diff scope. No step starts without this block.
- **G2 regenerate**: file produced *by the generator only* (diff = generator output; no
  manual edits to stamped files).
- **G3 build**: `mvn -q -o -DskipTests test-compile` green in `messaging/kafka`.
- **G4 tests**: `mvn -q -o test -pl messaging/kafka -am -Dtest='<new tests> + affected'`
  green, then full module `mvn -q -o test`.
- **G5 docs**: `doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md` coverage row updated
  (✓ + commit hash), `doc/REQUIREMENTS.md` entry appended, README/`doc/COMPLIANCE.md`
  touched only if user-visible.
- **G6 commit**: message `kafka-codec: <step> — <what> (<rows>)`, heredoc body,
  `Co-Authored-By: AI assistant`, branch `cleanup-messaging`. Never push.
- **G7 matrix**: `python3 gen_kafka.py matrix` row must match the plan doc row.

## 10. Pilot: Metadata (key 3) — S3/S4 concrete plan

State before pilot (verified 2026-09-28):
- `KafkaCodec.encodeMetadataRequest/Response` inline, **v0-only layout**
  (req: topics; resp: brokers(node,host,port), topics(error,name,partitions(error,index,
  leader,replicas,isr))). 64+ constructor call sites across
  `KafkaCodecTest` (27), `KafkaBroker` (9), `KafkaCodec` (16), `KafkaAdminClient`,
  `KafkaProducer`, `KafkaConsumer`.
- Data records are the v0 shapes (max-version fields absent); spec is v0–12, flexible 9+.

S3 (codec) — files in/out:
- in: `doc/spec/message/MetadataRequest.json`, `MetadataResponse.json`,
  `codec/KafkaCodecPrimitives.java` (API), `KafkaCodec.java` (delegation site)
- out: `codec/MetadataCodec.java` (generated), `KafkaCodec.java` (4 metadata methods →
  delegates; inline body deleted)
- **data records stay untouched** (hand-written; S2 regenerates them in a separate step
  with the compat-ctor audit). The generated codec accesses the **existing** v0 fields
  (`topics`, `allowAutoTopicCreation`, `brokers`, …). For spec fields not yet present in
  the current record the generator detects the model shape by parsing the existing
  `protocol/<Model>.java` (canonical + nested records) and emits:
  - **encode**: write the field at its spec wire position with the spec `default` value
    (ignorable/absent → §4 default); a comment marks it
    `// S2: field <name> pending data-class regeneration (spec default)`.
  - **decode**: read the field at its wire position and discard it into an `_ignore`
    local (no accessor exists yet).
  Fields occupy fixed wire positions and **must never be skipped**: skipping would
  shift every subsequent field and break round-trip against real brokers. Absent
  fields always encode with their spec default, so re-encoding a decoded value is
  byte-identical (§4), which is what the generated round-trip and byte-identity tests
  assert. This keeps S3 independently shippable; S2+ then upgrades the record and
  the codec regenerates with full fields (accessor read/write replaces default/write
  and read-and-discard).
- gates: G1–G6. Accept: module tests green (KafkaCodecTest metadata tests keep passing —
  they are round-trips through the façade, which is now v12 at the pinned version).

S4 (tests) — files in/out:
- out: `codec/MetadataCodecTest.java` (generated per §8; samples built from the **current**
  record shape; per-group round-trip + fall-through + v13 negative)
- gates: G1–G6.

## 11. No re-evaluation rules

1. This document is the single source of truth. A session must not re-derive type
   mappings, version rules, or group logic — it applies §2–§8 mechanically.
2. The JSON spec is the wire truth; if a hand-written codec disagrees with spec+this doc,
   the spec wins and the hand codec is replaced by a generated one (that is the point of
   phase 6a).
3. `gen_kafka.py` output is deterministic: same spec + same generator ⇒ identical bytes.
   Any manual edit to a stamped file is a defect to be re-derived and regenerated.
4. Version coverage is measured by `gen_kafka.py matrix` and the plan-doc table only.

## 12. Generator implementation notes (for `gen_kafka.py`)

- New functions: `java_type(spec_type, flexible)`, `present(f, v)`, `layout(fields, v,
  flexible)`, `group_versions(layout_fn)`, `emit_codec_*`, `emit_tests_*`,
  `field_defaults(f, v, java_type)`.
- Name mangling: spec `CamelCase` field → `camelCase` Java accessor; `snake_case` Java
  method; spec entity `MetadataResponseBroker` → nested record `BrokerMetadata` **only in
  the existing hand-written classes** — the generator uses the *existing* nested-record
  name discovered from the current data file when regenerating a carved API (pilot: read
  nested type from the current `.java`). For brand-new data emission (S2+), nested record
  name = spec entity minus the `<Model>` prefix.
- The existing generator's `PRIM` table is **retired** in favor of §2 (it referenced
  non-existent primitives); keep `java_type` as the sole mapping.
- Buffer: `BufferPool` is `ssg.legoflow.core.dp.BufferPool`
  (`getBuffer(int)` / `release(ByteBuffer)`), same as ProduceCodec.
- JSON5: strip full-line `//` comments (value lines never start with `//`).
