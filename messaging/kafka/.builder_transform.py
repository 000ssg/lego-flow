#!/usr/bin/env python3
"""Transform canonical-arity `new X(args)` call sites into `X.builder()...build()` chains.

Only rewrites a `new X(` expression when the number of top-level arguments equals the
canonical record arity. Compatibility-constructor sites (fewer args, different meaning)
are left untouched.

Robustness: a single pass over the ORIGINAL text tracks depth across () [] {} <> and
the string/char-literal state, so a comma inside `byte[]{1, 2}`, a generic type, or a
string literal is never mistaken for an argument separator, and no length mutation is
introduced (which is what broke earlier masking attempts).
"""
import sys

# (qualified Java type, [component names in canonical order]) — most-qualified first so
# `new A.B(` is never partially matched by the simple `B` needle.
RECORDS = [
    ("FetchResponse.PartitionResponse",
     ["partitionIndex", "errorCode", "highWatermark", "lastStableOffset",
      "logStartOffset", "abortedTransactions", "records"]),
    ("ProduceResponse.PartitionResponse",
     ["partitionIndex", "errorCode", "baseOffset", "logAppendTimeMs",
      "logStartOffset", "recordErrors", "errorMessage"]),
    ("FetchRequest.PartitionFetch",
     ["partition", "currentLeaderEpoch", "fetchOffset", "partitionMaxBytes",
      "logStartOffset"]),
    ("ApiVersionsResponse",
     ["errorCode", "apiKeys", "throttleTimeMs", "supportedFeatures",
      "finalizedFeaturesEpoch", "finalizedFeatures", "zkMigrationReady"]),
    ("SaslAuthenticateResponse",
     ["errorCode", "errorMessage", "authBytes", "sessionLifetimeMs"]),
    ("ProduceRequest", ["transactionalId", "acks", "timeoutMs", "topicData"]),
    ("FetchResponse", ["throttleTimeMs", "errorCode", "sessionId", "topics"]),
    ("FetchRequest", ["replicaId", "maxWaitMs", "minBytes", "maxBytes",
                     "isolationLevel", "sessionId", "sessionEpoch", "topics",
                     "forgottenTopics"]),
]

OPEN = "([{"
CLOSE = ")]}"
# NOTE: < > (generic/shift) are deliberately NOT depth-tracked. A comma inside a
# generic type (List<...>) is never a top-level argument separator, and `>>` /
# `>=` make < > unreliable as bracket pairs in Java. Tracking () [] {} is sufficient.


def parse_args(text: str, open_paren_idx: int):
    """Walk from an opening '(' and return (arg_list, close_paren_idx).

    Depth is tracked across () [] {} <>; string and char literals are skipped so a
    comma or bracket inside them is ignored. Returns the raw argument substrings
    (top-level, unstripped order preserved) and the index of the matching ')'.
    """
    n = len(text)
    depth = 1  # we start just inside the opening '('
    i = open_paren_idx + 1
    in_str = False
    in_chr = False
    in_line = False
    in_block = False
    args = []
    cur = []
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
            i += 1
            continue
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 2
                continue
            i += 1
            continue
        if in_str:
            if c == "\\":
                cur.append(c)
                if i + 1 < n:
                    cur.append(text[i + 1])
                i += 2
                continue
            cur.append(c)
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_chr:
            if c == "\\":
                cur.append(c)
                if i + 1 < n:
                    cur.append(text[i + 1])
                i += 2
                continue
            cur.append(c)
            if c == "'":
                in_chr = False
            i += 1
            continue
        # NORMAL
        if c == "/" and nxt == "/":
            in_line = True
            i += 2
            continue
        if c == "/" and nxt == "*":
            in_block = True
            i += 2
            continue
        if c == '"':
            cur.append(c)
            in_str = True
            i += 1
            continue
        if c == "'":
            cur.append(c)
            in_chr = True
            i += 1
            continue
        if c in OPEN:
            depth += 1
            cur.append(c)
            i += 1
            continue
        if c in CLOSE:
            depth -= 1
            if depth == 0:
                args.append("".join(cur))
                return args, i
            cur.append(c)
            i += 1
            continue
        if c == "," and depth == 1:
            args.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    raise ValueError("unbalanced parens starting at %d" % open_paren_idx)


def _all_sites(text: str):
    """Yield (pos, qtype, comps, args, close_idx) for EVERY `new X(` call of every
    record type, in source order. Qualified names (e.g. `new X.Nested(`) are skipped.
    `args` is the parsed top-level argument list (any arity)."""
    for qtype, comps in RECORDS:
        needle = "new " + qtype + "("
        idx = 0
        while True:
            pos = text.find(needle, idx)
            if pos == -1:
                break
            if pos > 0 and (text[pos - 1].isalnum() or text[pos - 1] == "_"):
                idx = pos + len(needle)
                continue  # qualified name; the '(' belongs to a nested type
            open_idx = pos + len(needle) - 1  # the '(' index
            try:
                args, close_idx = parse_args(text, open_idx)
            except Exception:
                idx = pos + len(needle)
                continue
            yield (pos, qtype, comps, args, close_idx)
            idx = close_idx + 1  # move past this whole call


def transform(text: str, max_passes: int = 5000):
    """Rewrite canonical-arity `new X(args)` call sites into `X.builder()...build()`.

    Repeatedly rewrites the *smallest-position* canonical site, then re-scans the
    (changed) text. This makes nesting safe: an outer `new FetchResponse(...)` is
    rewritten while its nested `new FetchResponse.PartitionResponse(...)` survives
    verbatim inside the new chain and is picked up on a later pass. The set of
    canonical `new X(` sites shrinks by one each pass, so it terminates."""
    count = 0
    for _ in range(max_passes):
        best = None
        for pos, qtype, comps, args, close_idx in _all_sites(text):
            if len(args) != len(comps):
                continue
            if best is None or pos < best[0]:
                best = (pos, qtype, comps, args, close_idx)
        if best is None:
            break
        pos, qtype, comps, args, close_idx = best
        chain = qtype + ".builder()"
        for comp, val in zip(comps, args):
            chain += "." + comp + "(" + val.strip() + ")"
        chain += ".build()"
        text = text[:pos] + chain + text[close_idx + 1:]
        count += 1
    return text, count


if __name__ == "__main__":
    path = sys.argv[1]
    with open(path) as f:
        src = f.read()
    out, count = transform(src)
    if "-w" in sys.argv:
        with open(path, "w") as f:
            f.write(out)
    print("rewrote %d call sites in %s" % (count, path))
