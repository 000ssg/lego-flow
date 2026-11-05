# Per-Version Codec Discipline (versioned protocol codecs)

How to write the unit tests and choose mechanisms for a versioned protocol codec
(Kafka `doc/spec/message/*.json` today; any protocol with per-version wire layouts). The always-on rules live in SKILL.md ("Versioned Codec Discipline"); this file is the
test-writing depth.

## gen_kafka.py: spec-first foundation (run before writing the codec)

At the start of a row, materialize and read the spec artifacts before writing any codec code:

- `python3 gen_kafka.py freeze <API> <Request>` and `... <Response>` — writes the frozen order tables to `doc/spec/order/<API>.<Direction>.txt`; they are the layout-group source of truth (field/tag placement per version) and are committed in the specs commit.
- `python3 gen_kafka.py delta <API> <vN> <Request|Response>` — prints the per-version field delta for the row; loop it over the row's versions to establish which are identical and which diverge. The version positional argument is REQUIRED — a call without it silently prints nothing, so empty output means wrong CLI form, NOT "no delta"; never conclude two versions are identical from an empty delta.
- `python3 gen_kafka.py order <API> <Request|Response> <vmin> <vmax>` — field order across a version range (the N-1 vs N diff used to verify wire-identical versions).

## Exact-byte test recipe (per version, per message)

1. **Derive the layout from the per-version spec, never from memory or wire captures.**
   Field ORDER comes from the spec JSON. A captured byte stream cannot show an ABSENT
   field, so it is structurally unqualified as a layout source. The module's
   `doc/spec/*.json` files open with a `//` license-comment block — not strict JSON:
   strip the lines whose stripped form starts with `//`, then `json.load` works;
   walk each field's `versions` range to confirm the exact per-version delta
   (a field can be `2+` in the response while its request-side sibling only
   appears in `3+`).
2. **Total body length = every field incl. every length prefix, INCLUDING the prefix
   of an empty string/bytes/array.** Common bug: an empty `string` still costs its 2-byte
   length prefix; an empty `bytes` field still costs its 4-byte length prefix.
3. **Assertions in order:**
   1. total length (`buf.remaining()` / array length) — the most common arithmetic
      failure (an off-by-one prefix is caught here first);
   2. each field's bytes at its spec position.
4. **Build the expected buffer with the SAME codec primitives the implementation uses**
   (`writeString`/`writeBytesField` …) so you test the layout, not the primitives. For
   ASCII string data, `"ok".getBytes(US_ASCII)` writes 2 bytes — `putChar` would write
   4 (UTF-16).
5. **Read the encoded buffer back with sequential buffer calls only.**
   - `buf.getChar()` reads 2 bytes as ONE UTF-16 char — `0x6f6b` ("ok") is one `getChar()`,
     not two bytes. For per-byte ASCII assertions use individual `buf.get()` calls.
   - Array-index reads (`arr[i]`) do NOT advance position. Consume data bytes through
     the buffer (`buf.get()`) before any following `getLong()`/`getInt()`; mixing the two
     desyncs every subsequent read by exactly the number of bytes read via array.
   - A hand-rolled varint reader (needed to walk tagged sections the codec itself skips) must implement LEB128 correctly: accumulate `b & 0x7F`, continue while `(b & 0x80) != 0` where `b` is the byte JUST read, shift by 7 per continuation. Testing the NEXT byte instead of the current one misparses every multi-byte varint and shifts the whole exact-byte walk — the walk fails on the first varint >= 128 (i.e. any tag size or count you'd actually assert on), which makes the bug look like a codec framing error.
6. **Records with `byte[]` fields: compare field-by-field.** Record `equals` uses
   reference equality for array components — `assertArrayEquals` per byte field, normal
   equality for the rest.
7. **Byte-identity versions** (spec-identical to vN−1): assert the encoded bytes at
   version N equal the vN−1 encoding for the same input (byte-for-byte), plus a decode
   round-trip at N to pin the decode side.

## Worked size arithmetic (catches hand-math errors before the test fails 6 times)

SaslAuthenticate response v1, non-empty fields:
2 (errorCode) + 2 (errorMessage length) + 2 ("ok" data) + 4 (authBytes length) + 2
(authBytes data) + 8 (sessionLifetimeMs) = **20 bytes**.

Same layout, all fields empty:
2 + 2 + 0 + 4 + 0 + 8 = **16 bytes** — note the empty errorMessage still carries its
2-byte length prefix; the empty authBytes still carries its 4-byte length prefix.

Compute the expected total in the test's comment from the field list; if the arithmetic
needs more than one pass, write it out in a scratch line before asserting — the failures
this session were all hand-arithmetic, not codec bugs.

## Generating the expected byte vectors (generate, don't hand-type)

Hand-typed multi-field byte arrays reliably contain errors — wrong big-endian splits
(int16 2000 = `{7, 208}` = 0x07D0; a mistyped `{7, 144}` passes the total-length assertion
and fails mid-field), wrong length prefixes, wrong totals — and each failure starts a
re-derive loop. Generate the array once, correctly:

1. **Build the expected byte sequence in a scratch Python script with `struct.pack`**, using
   exactly the encoding the spec prescribes: `>h` int16, `>i` int32, `>q` int64,
   `varint(len+1) + UTF-8` for compact strings, 2-byte length + bytes for non-flexible
   strings, `varint(n)` / `varint(n+1)` for compact array headers. Drive the field list
   from the frozen order table, and compute the total in the same script — if the Python
   total disagrees with the hand layout sum, one of the two is wrong (resolve per the next
   section before transcribing anything).
2. **Transcribe the generated values into the Java test with the project convention**:
   decimal literals, with an explicit `(byte)` cast on every value 128–255 — a bare
   decimal >127 in a `byte[]` initializer is a compile error, and the casted-decimal style
   matches the neighboring tests.
3. The spec-verified encoder output remains the arbiter when it disagrees with the
   generated vector — spec first, then reconcile per the next section.

## When the hand-computed total disagrees with the encoder output

A total-length assertion failure means EITHER the hand math is wrong OR the encoder is
wrong — decide which before editing. Resolving the direction first is what prevents
"fixing" the encoder to match a bad expectation (or vice versa):

1. **Dump the actual encoded hex with a scratch main**, not by guess:
   ```bash
   mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
   CP="target/classes:$(cat /tmp/cp.txt)"
   javac -cp "$CP" -d /tmp /tmp/Enc.java   # small main: encode one message, print len + hex
   java -cp "/tmp:$CP" Enc
   ```
   Gradle-built repo (or `mvn` not on PATH): compile the scratch main against the module's
   `build/classes/java/main` plus the `build/classes/java/main` dirs of the sibling modules
   whose classes it touches (e.g. the service util owning `BufferPool`):
   `javac -cp <module>/build/classes/java/main:<sibling>/build/classes/java/main /tmp/Dump.java -d /tmp`,
   then run with the same classpath. Parse the hex from the probe's own stdout, not from
   surefire/JUnit XML `system-out` (CDATA wrappers, line wrapping make it unreliable).

   **Hand-written byte literals: sign extension and endianness.** An int32/int64 −1 in a
   literal is all 0xFF bytes — `{(byte)-1,(byte)-1,(byte)-1,(byte)-1}`, NOT `{-1,0,0,0}`
   (little-endian sign confusion). Small positive big-endian ints are zero-padded FIRST:
   int32 1 = `{0,0,0,1}`, not `{1,0,0,0}`. Every sentinel −1 field in a message (replicaId,
   leaderEpoch, …) hits this trap, so one wrong sign-extension or endianness assumption fails
   ALL such assertions at once — the classic batch of "layout" failures where the codec
   itself is byte-correct and every failing assertion is a hand-written literal.
2. **Fastest arbiter when the encoder is already spec-verified** (verify its field
   order and widths against the spec at the start of the row — then the layout is
   not in question, only the arithmetic): count the total from the encoder's own
   write sequence — enumerate the `put*`/`write*` calls in the method, each call =
   one field (fixed width, or a prefix sized by the test data). Hand recomputation
   from the spec is where the total goes wrong (a per-partition base miscounted 4
   bytes short produced two consecutive wrong totals before count-from-source
   settled it). After the FIRST total mismatch, switch to count-from-source and have
   the test adopt that number — do not hand-patch the test a third time from a new
   mental sum; a repeated mismatch means re-read the encoder, not the spec.
3. **Verify the dump field-by-field against the per-version spec layout** (order,
   width, length prefixes, absent fields). Two outcomes:
   - encoder spec-correct → fix the TEST's expected total and re-derive its comment
     arithmetic from the field list (head-misadding sums like `2+4+4+3+4+4+4` is the
     usual culprit — re-add it in the comment, don't force it to match);
   - encoder deviates → fix the CODEC.
4. **After a quiet build (`mvn -q test`), confirm the numbers** by reading
   `target/surefire-reports/*.txt` (`grep "Tests run"`); quiet mode prints nothing on
   success, and the counts cited in commits/progress logs must come from these files.
   Build-environment trap: `mvn` is NOT on PATH by default on this machine — run
   `export PATH="$HOME/.sdkman/candidates/maven/current/bin:$HOME/.sdkman/candidates/java/current/bin:$PATH"`
   before any mvn invocation. When capturing an exit code from a redirect + pipeline,
   capture `$?` immediately after mvn itself, never after `tail`: a pipeline whose `mvn`
   never ran (missing Java) still exits 0, with "Unable to locate a Java Runtime" in the
   output — a fake success that looks identical to a real one until the output is read.
   Treat a run as real only when its output contains `Tests run` + `BUILD SUCCESS`. The Gradle fallback has a sibling trap: `./gradlew :<project>:test --tests '<FQN>'` can exit 1 with "No tests found" ev...[truncated]

## Evolving the model record when a version adds fields

When a version adds a field to the message model record (a response partition's
`logStartOffset`, a per-partition error list + trailing message, …):

- Add the field to the record's canonical all-components constructor AND provide a
  compat constructor that defaults the new field (null / empty) so ALL existing call
  sites — older-version tests, builders, fixtures — keep compiling unchanged. Do not
  rewrite every call site to add one field.
- If the row extends the canonical constructor instead of adding a compat constructor,
  find every constructor call site in the codec BEFORE the first build — every
  version's decode path that constructs the model uses it, not just the new version's
  methods: `grep -n 'new <Record>(' src/main/java/.../codec/`. The legacy older-version
  decodes are the usual stale sites; each is a compile error that only surfaces at
  build time, and the grep enumerates them all in one pass. Pass the spec absent-value
  ("" / -1 / null) at each, keeping those versions' output byte-identical.
- A new nested structure (e.g. an element of a per-partition error array) becomes its
  own record; its nullable fields default to null on the compat path.
- The older versions' exact-byte tests are the guard: if an older version's expected
  total length changes after the record extension, the encoder is emitting the new
  field at the older version — that is a dispatch/fall-through bug (SKILL.md pitfall
  20), NOT a test expectation to update.
- Response-only versions (request byte-identical to vN−1): the request dispatch switch
  is untouched — assert request byte-identity, and add the version to the encode and
  decode RESPONSE switches only (two separate switches; verify both groupings after
  the edit).

## Per-row commit housekeeping

- **Settle each codec in exactly 3 commits: specs → code → docs+matrix.** (1) Specs: only artifacts that actually exist on disk — the frozen order tables, plus spec JSONs when the repo carries them; when a message's nested-struct JSONs are not checked in, the specs commit carries just the order tables — never describe it as containing more. (2) Code: the codec, the model records, the shared-codec dispatch/delegation change, and the test file. (3) Docs+matrix: PROGRESS.md checklist + log entry, REQUIREMENTS.md commit section, the PHASE6A matrix row — and only tracked files; the in-flight ledger `doc/work/WIP.md` is committed WITH the code commit (the resumed session reads it first, so it must be in the tree — keep its `Activity` section naming the row in flight and its `...[truncated] Each commit message references the previous commit's short hash (code → specs, docs → code), keeping the chain auditable.
- **Drafted docs carry a `PENDING` SHA placeholder until the code commit lands; the backfill commit settles the row.** Docs drafted before the code commit exists (matrix row, progress log entry, requirements header) must carry `PENDING` in the matrix Commit column and progress entry — the code commit's short hash does not exist yet. After the code commit, replace every placeholder with the real SHA — `grep -rn PENDING` over the doc files and patch every hit, do not recall the count — and commit the backfill as a separate docs commit. A `PENDING` left in the tree is an unsettled row.
- **Matrix row numbers in progress logs are a GLOBAL sequential count of version rows, not file lines.** Before writing a row range for a new API, count version rows from the start of the version matrix and verify the counter reproduces the row range already cited for a known prior API entry (anchor on the last settled API). File-line numbers diverge from global row numbers by hundreds; citing one for the other corrupts the log entry, the matrix, and the commit message at once.
- **One API row is not a sub-category completion, and the done/remaining figures are a complement pair.** Before checking a PROGRESS.md sub-category row (Record I/O, Admin, …), count the rows in that section: the checklist is done-rows/total-rows across the whole section, and "subcat complete" language is only for its LAST row. Derive the done count by counting the actual version rows present in the matrix section (anchor on the last settled API, sum the row ranges it cites), NOT by adding prior APIs' counts from memory — a recalled running total drifts. The `done/total` and `remaining (N)` figures are complements (remaining = total − done), so a single miscounted done-count corrupts the remaining count in the same line; the pair staying self-consistent (done + remaining = total) is NOT validation, because the wrong remaining is derived from the wrong done count and the invariant still holds — only an actual count of matrix rows validates it. When you correct one, recompute and patch the complement in the same pass and re-verify both against the matrix.
- **WIP/recovery-ledger claims are unverified assertions — verify before acting on them or repeating them.** "Already committed in <sha>" claims check out with `git show <sha> --stat`; file conventions stated in the ledger (which docs get committed, which stay local) check out against `git log --all -- <path>`. Ledgers written mid-session go stale on both counts.
- **After any interruption, re-derive state from git, not from the handoff summary.** `git log --oneline`, `git status --porcelain`, `git show --stat` on the expected commits: a stopped session may have executed queued commands after the snapshot, so work the summary lists as pending can already be committed. Verify against the true HEAD, then continue.
- **Every number cited in the commit message / progress log / requirements entry must be derived from the tree, never recalled.** Test-count deltas and line stats come from the working tree, not memory: `git show <code-sha>:<test-file> | grep -c '@Test'` for the baseline, `grep -c '@Test' <test-file>` for the new, `git show <sha> --stat` for line counts — and cite only what you just measured. Guessing the delta (the classic off-by-N: writing +8 when the row added 6) cascades into the code commit message, the matrix row, PROGRESS.md, and REQUIREMENTS.md — four places to fix plus an amend. The rule extends from numbers to test-behavior claims: the per-version size/shape diffs cited in a progress log entry must be read from the test file's ACTUAL assertions (grep the shape-diff tests) before writing the entry, not composed from the spec's field list — the spec says what fields exist; the test says what arithmetic was actually asserted, and the two can differ.
- **Amend the code commit BEFORE the docs commit.** The docs commit's subject cites the code commit's short hash; the matrix row and progress log cite it too. While the branch tip is still your code commit, `git commit --amend` its message freely (a message fix is a rewrite, not a new commit). If the code commit is amended AFTER the docs commit landed, the hash in the subject + matrix row + progress entry + requirements header all point at a dead hash — re-derive the new hash and patch all four doc locations, then `git commit --amend` the docs commit too. The pre-commit hook's `No Java files in this commit` line on a docs-only amend is expected, not a warning.
- Long commit messages carrying quotes, parentheses, or `+` fragments: if the
  `$(cat <<'EOF')` heredoc breaks on shell quoting, write the message to a file under
  `/tmp` and `git commit -F /tmp/msg` — NEVER in the repo root, where `git add` picks
  the scratch file up and it must be removed with `git rm --cached` +
  `git commit --amend --no-edit`. Keep all scratch files (probe mains, classpath
  dumps, message files) under `/tmp`, mirroring the hex-dump recipe above.
- **Matrix commit message: write it from the CURRENT row's data, not by copying the
  previous row's message.** The one-line subject cites THIS row's code commit hash in the form the immediately preceding row's docs commit used (current house form: `kafka-codec: docs — backfill <API> commit SHA <sha> into matrix/progress/requirements`; an earlier form also seen in the repo: `kafka-codec: matrix — record <code-hash> as the <API> v<N> row commit hash`); the body cites this row's version, mechanism, and test-count delta. Copying the previous row's message verbatim is how the wrong hash lands in the subject — catch it before committing (or `git commit --amend` the subject afterwards; amend is fine while the branch tip is still your matrix commit).
- **Matrix table edit: anchor the patch to the target row ONLY.** Replacing the `| vN | ... | ☐ | |` row with its `| vN | ... | ✓ | <hash> |` form must not swallow adjacent rows — a fuzzy-matched edit anchored to a short line is what deletes the vN+1..vN+k rows sitting below it (or above). After the matrix edit, re-read the whole table block and confirm every version row is present with its status/hash intact before committing.
- **REQUIREMENTS.md: anchor the append to the file's tail, not to the previous entry's body.** The entry goes after the LAST commit section, before the `## Document Maintenance` footer. A patch anchored to an earlier entry's marker (its `Tests added` line, its `---`) fails once a later entry has been appended since the anchor was read — re-`grep -n '^## Commit'` to find the true last entry, then anchor to its footer + the maintenance footer. PROGRESS.md is the mirror case: its log is newest-first, so anchor the new entry under the `## Log` header.

## Kafka flexible-encoding (v3+) wire facts

Pinned from the 3.6.1 generated sources (`message/*Data.java`) — the spec JSON does not
carry these, so read the generated classes before writing a flexible version:

- **Tagged-fields section framing = LEADING count varint, no trailing sentinel.** Each
  field is: tag varint + size varint + payload bytes. A struct/element carries its own
  (empty) trailing tagged count in flexible versions (e.g. every `ApiVersion` element
  ends with `varint(0)`).
- **Tag payload sizes are exact, computed with `varintSize`** — the size varint covers
  only the payload, and an implicit (compact) array's `varint(n+1)` header IS inside
  that payload. Per element of a compact-string list: `varintSize(nameLen+1) + nameLen
  + <fixed-width fields> + 1` (the per-element tagged count). A fixed per-element
  estimate makes the decoder skip past the next tag.
- **Field widths are NOT implied by the spec** — feature version fields in ApiVersions
  v3 are int16, not int32; and compact-array SIZES are never implied either (a nullable
  compact array is `varint(0)`=null, `varint(n+1)`=n elements; a non-nullable one is
  `varint(n)`). Check the generated class's `writeTo`/`readFrom` for the exact form.
- **Wire order can deviate from logical order** — 3.6.1 `FinalizedFeatureKey` writes
  `maxVersionLevel` BEFORE `minVersionLevel`; mirror it exactly.
- **Null STRING width is mode-dependent** — non-flexible nullable STRING null = `int16(-1)` = 2 bytes `FF FF`; flexible nullable compact string null = `varint(0)` = 1 byte. A message present in both pre-flexible and post-flexible versions (e.g. FindCoordinator v1/v2 vs v3, same fields, only the encoding mode changes) has a DIFFERENT total for the same input: the null field costs 2 bytes in the non-flexible versions and 1 byte in the flexible ones. Compute each version's total independently from its own write sequence; do not take one version's total and assert it for all — the first-fix-round failure in this class is setting the non-flexible total to the flexible value (or vice versa) and breaking exactly those two versions.
- **Integer widths in flexible mode are FIXED — int16/int32/bool/uuid are NOT varint and have NO null sentinel in either mode** (pinned from the 3.6.1 codegen `message/Type.java`: `INT16.readFrom` = `in.readShort()` / `writeTo` = `out.writeShort(v)`, `INT32` = `readInt`/`writeInt`). A nullable int16/int32 in a flexible version writes a 1-byte presence varint (1=present, 0=null) then the FIXED-WIDTH value; in a non-flexible version it is the bare fixed-width value with the spec's absent/sentinel (−1) standing in for null. This is the single most likely false-alarm in a WIP ledger ("this int should be a varint / 5-byte null sentinel") — it is not, for int16/int32. Verify any such ledger flag against the codegen source before touching the codec.
- **When the spec JSON and the repo doc-convention disagree on a wire-encoding fact, the in-repo validated artifacts win — and the web KIP is last resort, not the arbiter.** The arbitration ladder, in order: (1) the code-generator's own output — `gen_kafka.py` frozen order tables (`order <API> <Request|Response> <vmin> <vmax>`) for field/tag placement, and the reference release's codegen source (`message/Type.java`-style) for exact widths; (2) the module's convention doc (e.g. `doc/CODEC_GENERATION_SPEC.md`) and the ALREADY-SETTLED validated codecs in the module (they are byte-verified against the broker, so they outrank any prose doc); (3) only then the web spec/KIP text. Do not spend the turn fetching web protocol docs when the in-repo artifacts answer the question. Specific case settled: tagged fields (e.g. `TopicConfigErrorCode` tag 0) belong in the TRAILING tagged section AFTER all non-tagged fields, even when the spec JSON lists them mid-struct — the spec JSON's apparent position is a red herring; confirm placement with the generator's frozen order table. And the spec JSON itself, in an older release tag, lives at `clients/src/main/resources/common/message/<Msg>.json` (the `metadata/` split came later — a `docs/protocol.md` or `kafka-website` fetch will 404; the `clients/` path is the working one).
- **Decode-side buffer-desync trap for nullable strings** — after reading a nullable string's length prefix, actually SKIP its payload bytes when the length is positive (the payload must be consumed even though the value is discarded), and read the compact-nullable length as a varint, not a plain `getInt()`. Leaving the payload bytes unread desyncs the buffer so EVERY subsequent field reads garbage, and the failure looks like a layout/width bug when it is a missing skip. A total-length assertion catches it late; read the length, `position += len` when `len > 0`.
- **Non-nullable compact string**: `varint(len+1) + UTF-8` with null→empty (both
  encode as `varint(1)`); round-trip must map empty back to the client's null default
  so decode→encode is byte-stable.
- **Absent optional tags decode to record defaults** (empty lists, sentinel epoch,
  false flags) and the encoder omits absent tags — so decode→re-encode of a minimal
  message must be byte-identical; that is the round-trip test to write.
- **Exact-byte vectors first** — write the full hand-computed byte array for the
  minimal message (every byte asserted) and the all-tags message; a single combined
  vector catches both the size arithmetic and the per-field offsets. When splicing
  bytes into a vector, reuse the real encoder output as the base and override only
  the bytes under test — hardcoding multi-field sequences (endianness, width) is how
  vector bugs creep in. Big-endian: `putInt(150)` = `00 00 00 96`.
- **New KIP-482 APIs are flexible from v0** — an API introduced after the f...[truncated]
