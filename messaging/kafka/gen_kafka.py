#!/usr/bin/env python3
"""gen_kafka.py — spec-first generator for Kafka codec version sub-tasks.

Why this exists
---------------
Every Phase 6a sub-task ("implement <API> v<N> encode+decode + tests") starts
with the same slow ritual: open the spec JSON, work out which fields exist at
vN, diff against vN-1, remember nullability, tagged fields and the
flexible-encoding cutover, then hand-write the encode/decode skeleton. That
ritual takes 10-20 min of attention per sub-task across ~210 sub-tasks.

This script makes the spec the single source of truth (see
`doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md` invariants #2/#3) and
emits, deterministically:

  delta     field-level vN vs vN-1 for <Api>{Request,Response}:
            full field list at vN (name, type, version range, null/tagged),
            + Δ (added/removed/changed) per top-level AND nested struct field,
            + flexible-encoding cutover marker, in plan-doc Δ-table format.
  order     BINDING wire processing order for vLo..vHi (default: full spec
            range): layout groups (identical layout = fall-through candidates,
            derived mechanically — never hand-derived) + field lines per group
            (fixed section first, trailing tagged section last, ascending tag).
            This is the field-order source of truth for codecs; it is a pure
            function of the spec JSON (see CODEC_GENERATION_SPEC.md).
  freeze    materialize the binding order table to doc/spec/order/<Api>.<Kind>.txt;
            refuses to overwrite a differing table (order change = spec
            amendment requiring review).
  matrix    regenerate the whole per-API Δ column (verifies plan-doc rows).
  skeleton  Java encode/decode stubs for version vN mirroring the existing
            FetchCodec style (fixed section, array loops, tagged sections):
            spec-ordered field lines with put*/get*/writeVarint calls, null
            and tagged markers, and TODOs where the model getter mapping is
            non-trivial (compact strings, uuid, records, arrays of structs).

Spec schema (apache/kafka 3.6.1 message *.json, hand-annotated with '//'
lines which are stripped before json.parse):
  validVersions "0-15", flexibleVersions "12+", fields[] with type /
  versions / nullableVersions / taggedVersions / tag / default / ignorable,
  and nested structs INLINE as `fields` on a `[]Struct` or `Struct` field.
  Nothing here touches the wire layout itself — it only REPORTS the spec;
  the mechanism choice (dedicated methods vs parameterizing vN-1) stays in
  the commit message per the sub-task contract.

Usage:
  gen_kafka.py delta <Api> <vN> [Request|Response]     # default: both kinds
  gen_kafka.py order <Api> <Request|Response> [vLo] [vHi]
  gen_kafka.py freeze <Api> <Request|Response>
  gen_kafka.py matrix [Api ...]                        # Δ tables for APIs
  gen_kafka.py skeleton <Api> <vN>                     # Java stubs (both kinds)
  gen_kafka.py apis                                    # list APIs + version ranges
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

SPEC_DIR = Path(__file__).resolve().parent / "doc" / "spec" / "message"

INF = 10 ** 9

# primitive type -> (write call, read call, fixed bytes)
PRIM = {
    "int8":   ("buf.put((byte) v)",     "buf.get()",                1),
    "int8s":  ("buf.put((byte) v)",     "buf.get()",                1),
    "uint8s": ("buf.put((byte) v)",     "buf.get()",                1),
    "int16":  ("buf.putShort(v)",       "buf.getShort()",           2),
    "uint16": ("buf.putShort(v)",       "buf.getShort()",           2),
    "int32":  ("buf.putInt(v)",         "buf.getInt()",             4),
    "uint32": ("buf.putInt(v)",         "buf.getInt()",             4),
    "int64":  ("buf.putLong(v)",        "buf.getLong()",            8),
    "uint64": ("buf.putLong(v)",        "buf.getLong()",            8),
    "bool":   ("KafkaCodecPrimitives.writeBool(buf, v)", "KafkaCodecPrimitives.readBool(buf)", 1),
    "uuid":   ("buf.put(v)",            "readUuid(buf)",            16),
    "string": ("KafkaCodecPrimitives.writeString(buf, v)", "KafkaCodecPrimitives.readString(buf)", None),
    "records": ("KafkaCodecPrimitives.writeNullableBytes(buf, v)", "KafkaCodecPrimitives.readNullableBytes(buf)", None),
    "byte":   ("KafkaCodecPrimitives.writeNullableBytes(buf, v)", "KafkaCodecPrimitives.readNullableBytes(buf)", None),
    "bytes":  ("KafkaCodecPrimitives.writeNullableBytes(buf, v)", "KafkaCodecPrimitives.readNullableBytes(buf)", None),
}
JAVA_TYPE = {
    "int8": "byte", "int8s": "byte", "uint8s": "byte",
    "int16": "short", "uint16": "short",
    "int32": "int", "uint32": "int",
    "int64": "long", "uint64": "long",
    "bool": "boolean", "uuid": "byte[]",
    "string": "String", "records": "byte[]", "byte": "byte[]", "bytes": "byte[]",
}


def load_spec(path: Path) -> dict:
    raw = path.read_text()
    # spec files carry '//' annotation lines that break json.load
    cleaned = "\n".join(l for l in raw.splitlines() if not l.lstrip().startswith("//"))
    # object_pairs_hook preserves the JSON *array* field order through every
    # dict level — the binding wire order (doc/CODEC_GENERATION_SPEC.md
    # "Field processing order"). Never replace this with json.load on
    # unordered structures: field order is data, not presentation.
    return json.loads(cleaned, object_pairs_hook=dict)


def parse_range(rng: str | None) -> tuple[int, int] | None:
    """'12+' -> (12, INF); '7-12' -> (7, 12); '0' -> (0, 0); None -> all."""
    if rng is None:
        return None
    rng = str(rng).strip()
    if rng.endswith("+"):
        return (int(rng[:-1]), INF)
    if "-" in rng:
        a, b = rng.split("-", 1)
        return (int(a), int(b))
    if re.fullmatch(r"\d+", rng):
        n = int(rng)
        return (n, n)
    return None


def present_at(f: dict, v: int) -> bool:
    rng = parse_range(f.get("versions"))
    return rng is None or rng[0] <= v <= rng[1]


def spec_max(spec: dict) -> int:
    lo, hi = spec["validVersions"].split("-")
    return int(hi)


def flex_lo(spec: dict) -> int | None:
    fv = spec.get("flexibleVersions")
    if not fv:
        return None
    return int(str(fv).rstrip("+").split("..")[0])


def flex_at(spec: dict, v: int) -> bool:
    lo = flex_lo(spec)
    return lo is not None and v >= lo


def is_array(t: str) -> bool:
    return isinstance(t, str) and t.startswith("[]")


def elem_type(t: str) -> str:
    return t[2:] if is_array(t) else t


def type_of(f: dict) -> str:
    t = f.get("type")
    return t if isinstance(t, str) else str(t)


def field_sig(f: dict) -> str:
    """name:type — the Δ-table cell text (null/tagged noted separately in full list)."""
    t = type_of(f)
    v = f.get("versions")
    tag = ""
    if v and v not in ("0+",):
        tag = f"[{v}]"
    return f"{f['name']}:{t}{tag}"


def collect(spec: dict, v: int):
    """Yield (path, field, flexible) for every field present at v, recursion into nested structs."""
    def rec(fs: list, path: str, flexible: bool):
        for f in fs:
            if not present_at(f, v):
                continue
            nm = f"{path}.{f['name']}" if path else f["name"]
            yield nm, f, flexible
            if f.get("fields"):
                nested_flex = flexible or flex_at(spec, v)
                yield from rec(f["fields"], nm, nested_flex)
    yield from rec(spec.get("fields", []), "", flex_at(spec, v))


def diff(spec: dict, v: int) -> dict:
    if v <= 0:
        return {"added": [], "removed": [], "changed": []}
    prev = {p: f for p, f, _ in collect(spec, v - 1)}
    curr = {p: f for p, f, _ in collect(spec, v)}
    added = [f"+ {field_sig(curr[p])}" for p in curr if p not in prev]
    removed = [f"- {field_sig(prev[p])}" for p in prev if p not in curr]
    changed = [f"~ {p}: {type_of(prev[p])}/{prev[p].get('nullableVersions')} -> {type_of(curr[p])}/{curr[p].get('nullableVersions')}"
               for p in curr if p in prev
               and (type_of(prev[p]) != type_of(curr[p])
                    or prev[p].get("nullableVersions") != curr[p].get("nullableVersions"))]
    return {"added": added, "removed": removed, "changed": changed}


def delta(api: str, v: int, kind: str) -> str:
    path = SPEC_DIR / f"{api}{kind}.json"
    if not path.exists():
        sys.exit(f"no spec file {path}")
    spec = load_spec(path)
    max_v = spec_max(spec)
    if v > max_v:
        sys.exit(f"{api} {kind}: v{v} exceeds max v{max_v}")
    L = [f"== {api} {kind} v{v} ==  versions {spec['validVersions']}"
         + (f", flexible from v{flex_lo(spec)}" if flex_lo(spec) else "")]
    for p, f, flexible in collect(spec, v):
        t = type_of(f)
        notes = []
        if f.get("nullableVersions"):
            notes.append(f"null:{f['nullableVersions']}")
        if f.get("taggedVersions"):
            notes.append(f"tag{f.get('tag')}:{f['taggedVersions']}")
        if f.get("versions"):
            notes.append(f"v{f['versions']}")
        if flexible:
            notes.append("flex")
        L.append(f"  {p:<44} {t:<22} {' '.join(notes)}")
    L.append("")
    d = diff(spec, v)
    L.append(f"Δ vs v{v - 1 if v > 0 else 0} (plan-doc format):")
    if v == 0:
        L.append("  base")
    else:
        body = [s.lstrip("+-~ ").replace(": ", ":") for s in d["added"] + d["removed"] + d["changed"]]
        fm = f"→ flexible encoding" if flex_lo(spec) == v else None
        parts = [x for x in body if x]
        if fm:
            parts.append(fm)
        if not parts:
            L.append("  unchanged")
        else:
            L.append("  " + "<br>".join(parts))
    return "\n".join(L)


def matrix(apis: list[str]) -> str:
    out = []
    for api, desc in list_apis():
        if apis and api not in apis:
            continue
        out.append(f"### {api} — {desc}")
        for kind in ("Request", "Response"):
            path = SPEC_DIR / f"{api}{kind}.json"
            if not path.exists():
                continue
            spec = load_spec(path)
            max_v = spec_max(spec)
            out.append(f"\n| v | Δ vs previous ({kind}) |")
            out.append("|---|---|")
            for v in range(max_v + 1):
                d = diff(spec, v)
                if v == 0:
                    n = len([1 for _ in collect(spec, 0)])
                    out.append(f"| v0 | base ({n} fields) |")
                    continue
                parts = [s.lstrip("+-~ ") for s in d["added"] + d["removed"] + d["changed"]]
                if flex_lo(spec) == v:
                    parts.append("→ flexible encoding")
                out.append(f"| v{v} | {' '.join(parts) if parts else 'unchanged'} |")
    return "\n".join(out)


def list_apis():
    apis = {}
    for p in sorted(SPEC_DIR.glob("*.json")):
        m = re.fullmatch(r"(\w+)(Request|Response)", p.stem)
        if not m:
            continue
        try:
            spec = load_spec(p)
        except (json.JSONDecodeError, KeyError):
            apis.setdefault(m.group(1), {}).setdefault(m.group(2), "UNSTABLE")
            continue
        apis.setdefault(m.group(1), {})[m.group(2)] = spec["validVersions"]
    return sorted(
        (name, f"req {d.get('Request', '?')} / resp {d.get('Response', '?')}")
        for name, d in apis.items()
    )


def snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


# ===== Field processing order (binding; see doc/CODEC_GENERATION_SPEC.md) =====
#
# The order is a pure function of the spec JSON: fields[] array order, plus
# tagged fields (taggedVersions set) appended at the trailing tagged section
# in ascending tag order, per struct level. No inference, no human re-derivation.

def layout_sig(spec: dict, v: int) -> str:
    """Colon-joined wire order at v, RECURSIVE over nested struct fields.

    Identical sig for a version run == byte-identical field layout (fall-through
    candidate); a sig change marks a version group boundary. The flex state is
    part of the sig: a flexible-encoding cutover changes every string/bytes/array
    prefix, so a flex-only change must break the run too.
    """
    parts = [p for p, _f in _order_recurse(spec.get("fields", []), v)]
    sig = ":".join(parts)
    return sig + ("|flex" if flex_at(spec, v) else "")


def _order_recurse(fs: list[dict], v: int, path: str = "") -> list[tuple[str, dict]]:
    """(qualified-name, field) pairs in wire-processing order (fixed first per
    level, trailing tagged last in ascending tag), recursing into nested structs
    at their position. Nested struct contents are appended right after the
    struct field, so the sig reflects the full per-element layout."""
    out: list[tuple[str, dict]] = []
    fixed = [f for f in fs if present_at(f, v) and not f.get("taggedVersions")]
    tagged = sorted(
        (f for f in fs if present_at(f, v) and f.get("taggedVersions")),
        key=lambda f: int(f["tag"]),
    )
    for f in fixed + tagged:
        tag = f"({path})" if path else ""
        if f.get("taggedVersions"):
            sig_name = f"{tag}{f['name']}[tag{f['tag']}]"
        elif f.get("versions") and f["versions"] != "0+":
            sig_name = f"{tag}{f['name']}?"
        else:
            sig_name = f"{tag}{f['name']}"
        out.append((sig_name, f))
        if f.get("fields"):
            out.extend(_order_recurse(f["fields"], v, f["name"]))
    return out


def _group_runs(pairs: list[tuple[int, str]]) -> list[str]:
    """[(v, sig), ...] -> ['v0-v2 -> sig...', 'v3 -> sig...'] compact runs."""
    runs: list[str] = []
    i = 0
    while i < len(pairs):
        v, sig = pairs[i]
        j = i
        while j + 1 < len(pairs) and pairs[j + 1][1] == sig:
            j += 1
        span = f"v{v}" if j == i else f"v{v}-v{pairs[j][0]}"
        runs.append(f"{span} -> {sig}")
        i = j + 1
    return runs


def order(api: str, v_lo: int | None, v_hi: int | None, kind: str, show_lines: bool) -> str:
    path = SPEC_DIR / f"{api}{kind}.json"
    if not path.exists():
        sys.exit(f"no spec file {path}")
    spec = load_spec(path)
    max_v = spec_max(spec)
    lo = 0 if v_lo is None else v_lo
    hi = max_v if v_hi is None else min(v_hi, max_v)
    if lo > hi:
        sys.exit(f"empty version range v{lo}..v{hi}")
    flo = flex_lo(spec)
    L = [f"{api} {kind} — field processing order (binding; spec 3.6.1)"
         + (f" — flexible encoding from v{flo}" if flo else "")]
    pairs = [(v, layout_sig(spec, v)) for v in range(lo, hi + 1)]
    L.append("wire order by layout (equal run = byte-identical field layout):")
    L.extend("  " + r for r in _group_runs(pairs))
    if show_lines:
        L.append("field lines (fixed section first, trailing tagged section last, ascending tag):")
        for run in _runs(pairs):
            L.append("  " + _fmt_field_lines(spec, *run))
    return "\n".join(L)


def _runs(pairs: list[tuple[int, str]]) -> list[tuple[int, int]]:
    """[(v, sig), ...] -> [(v_start, v_end)] of contiguous equal-sig runs."""
    runs: list[tuple[int, int]] = []
    i = 0
    while i < len(pairs):
        j = i
        while j + 1 < len(pairs) and pairs[j + 1][1] == pairs[i][1]:
            j += 1
        runs.append((pairs[i][0], pairs[j][0]))
        i = j + 1
    return runs


def _fmt_field_lines(spec: dict, v_lo: int, v_hi: int) -> str:
    flo = flex_lo(spec)
    lines = [f"v{v_lo}" + (f"-v{v_hi}" if v_hi > v_lo else "") + ":"]
    for v in range(v_lo, v_hi + 1):
        fs = [f for f in spec.get("fields", []) if present_at(f, v)]
        fixed = [f for f in fs if not f.get("taggedVersions")]
        tagged = sorted((f for f in fs if f.get("taggedVersions")), key=lambda f: int(f["tag"]))
        f1 = ", ".join(f"{f['name']}({type_of(f)})"
                       + (" [flex]" if flo is not None and v >= flo else "")
                       for f in fixed) or "∅"
        f2 = ", ".join(f"tag{f['tag']} {f['name']}({type_of(f)})" for f in tagged) or "∅"
        lines.append(f"  v{v}: fixed: {f1} || tagged: {f2}")
    return "\n".join(lines)


def freeze(api: str, kind: str) -> None:
    """Write the binding order table to doc/spec/order/<Api>.<kind>.txt.

    Refuses to overwrite a differing file: any order change is a spec
    amendment requiring review, never a silent generator rewrite.
    """
    text = order(api, None, None, kind, show_lines=True)
    out = Path(__file__).resolve().parent / "doc" / "spec" / "order" / f"{api}.{kind}.txt"
    if out.exists() and out.read_text() != text:
        sys.exit(f"{out} differs — field order changed vs the frozen table; "
                 "review the spec delta before re-freezing (CODEC_GENERATION_SPEC.md, "
                 "section 'Field processing order')")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text)
    print(f"frozen: {out}")


def skeleton(api: str, v: int) -> str:
    out = []
    for kind, var in (("Request", "req"), ("Response", "resp")):
        path = SPEC_DIR / f"{api}{kind}.json"
        if not path.exists():
            continue
        spec = load_spec(path)
        flex = flex_at(spec, v)
        ver = f"V{v}"
        if kind == "Request":
            out.append(f"private static byte[] encode{api}Request{ver}({api}Request {var}) {{")
            out.append("    // TODO: compute size (see v12 comment style), BufferPool.getBuffer(size)")
            for p, f, fl in collect(spec, v):
                t = type_of(f)
                note = ""
                if f.get("nullableVersions"):
                    note += " // nullable"
                if f.get("taggedVersions"):
                    note += f" // TAGGED tag{f.get('tag')} {f['taggedVersions']}"
                if f.get("versions") and f["versions"] != "0+":
                    note += f" // v{f['versions']}"
                out.append(f"    // {p} :: {t}{note}")
                if not fl and t in PRIM:
                    # PRIM[t][0] is a template ending in the "v" placeholder, e.g. "buf.putInt(v)"
                    expr = f"{var}.{snake(f['name'])}()"
                    w = PRIM[t][0][:-2] + expr + ")"
                    out.append(f"    {w};  // TODO verify getter")
                elif fl and t == "string":
                    nullable = f.get("nullableVersions")
                    suffix = " // nullable: writeVarint(0) when null" if nullable else ""
                    out.append(f"    // TODO compact string: writeCompactStringNonNullable(buf, {var}.{snake(f['name'])}){suffix}")
                elif fl and t == "uuid":
                    out.append(f"    // TODO uuid: buf.put({var}.{snake(f['name'])})  (16 bytes)")
                elif fl and t == "records":
                    out.append(f"    // TODO flexible nullable bytes: records")
                elif is_array(t):
                    et = elem_type(t)
                    out.append(f"    // TODO {t} array: varint(count+1) + loop  [{et}]")
                    if f.get("fields"):
                        out.append(f"    //   nested: {', '.join(ff['name'] for ff in f['fields'])}")
                elif t in ("TaggedFields", "TaggedFieldsV2"):
                    out.append(f"    // TODO tagged section: writeVarint(tagCount) [+ tags]")
                else:
                    out.append(f"    // TODO {t}: map")
            out.append("    // buf.flip(); return KafkaCodecPrimitives.toBytes(buf);")
            out.append("}")
        else:
            out.append(f"private static {api}Response decode{api}Response{ver}(ByteBuffer buf) {{")
            for p, f, fl in collect(spec, v):
                t = type_of(f)
                note = ""
                if f.get("nullableVersions"):
                    note += " // nullable"
                if f.get("taggedVersions"):
                    note += f" // TAGGED tag{f.get('tag')} {f['taggedVersions']}"
                if f.get("versions") and f["versions"] != "0+":
                    note += f" // v{f['versions']}"
                out.append(f"    // {p} :: {t}{note}")
                if not fl and t in PRIM:
                    out.append(f"    // {JAVA_TYPE[t]} {snake(f['name'])} = {PRIM[t][1]};")
                elif fl and t == "string":
                    out.append(f"    // String {snake(f['name'])} = KafkaCodecPrimitives.readCompactString{'NonNullable' if not f.get('nullableVersions') else ''}(buf);")
                elif fl and t == "uuid":
                    out.append(f"    // byte[] {snake(f['name'])} = readUuid(buf);  // 16 bytes")
                elif fl and t == "records":
                    out.append(f"    // byte[] {snake(f['name'])} = readNullableBytes(buf);")
                elif is_array(t):
                    out.append(f"    // TODO {t} array: varint(count) - 1 + loop  [{elem_type(t)}]")
                elif t in ("TaggedFields", "TaggedFieldsV2"):
                    out.append(f"    // TODO tagged section: skipTaggedFields(buf) or parse tags")
                else:
                    out.append(f"    // TODO {t}: map")
            out.append(f"    // return new {api}Response(...);  // canonical ctor/builder")
            out.append("}")
        out.append(f"// dispatch: case {v}: → encode/decode{api}{'Request' if kind == 'Request' else 'Response'}{ver}")
        out.append("")
    return "\n".join(out)


def usage() -> None:
    sys.exit(__doc__)


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        usage()
    cmd = sys.argv[1]
    if cmd == "apis":
        for name, desc in list_apis():
            print(f"{name:<36} {desc}")
        return
    if cmd == "delta":
        if len(sys.argv) < 4:
            sys.exit("usage: gen_kafka.py delta <Api> <vN> [Request|Response]")
        api, v = sys.argv[2], int(sys.argv[3])
        kinds = [sys.argv[4]] if len(sys.argv) > 4 else ["Request", "Response"]
        for kind in kinds:
            print(delta(api, v, kind))
            print()
        return
    if cmd == "matrix":
        print(matrix(sys.argv[2:]))
        return
    if cmd == "order":
        if len(sys.argv) < 4:
            sys.exit("usage: gen_kafka.py order <Api> <Request|Response> [vLo] [vHi]")
        api, kind = sys.argv[2], sys.argv[3]
        v_lo = int(sys.argv[4]) if len(sys.argv) > 4 else None
        v_hi = int(sys.argv[5]) if len(sys.argv) > 5 else None
        print(order(api, v_lo, v_hi, kind, show_lines=True))
        return
    if cmd == "freeze":
        if len(sys.argv) < 4:
            sys.exit("usage: gen_kafka.py freeze <Api> <Request|Response>")
        freeze(sys.argv[2], sys.argv[3])
        return
    if cmd == "skeleton":
        if len(sys.argv) < 4:
            sys.exit("usage: gen_kafka.py skeleton <Api> <vN>")
        print(skeleton(sys.argv[2], int(sys.argv[3])))
        return
    usage()


if __name__ == "__main__":
    main()
