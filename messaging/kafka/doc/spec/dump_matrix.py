#!/usr/bin/env python3
"""
Generate the §4 version sub-task matrix for doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md
from messaging/kafka/doc/spec/message/*.json (apache/kafka 3.6.1, verbatim).

Usage:
    python3 dump_matrix.py > /tmp/matrix.md
Then splice the API sections into the plan's §4 (structure/headers live in the plan).

One table row per API version: the Δ vs the previous version, from the schema's
per-field version gates (incl. inline nested component fields).
"""
import json
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "..", "..", "..")  # repo root
MSG = os.path.dirname(os.path.abspath(__file__))

CATEGORIES = [
    ("Record I/O", ["Produce", "Fetch", "ListOffsets"]),
    ("Metadata/Cluster", ["Metadata", "LeaderAndIsr", "StopReplica", "UpdateMetadata",
                          "ControlledShutdown", "OffsetForLeaderEpoch"]),
    ("Consumer Groups", ["OffsetCommit", "OffsetFetch", "FindCoordinator", "JoinGroup",
                         "Heartbeat", "LeaveGroup", "SyncGroup", "DescribeGroups",
                         "ListGroups", "OffsetDelete"]),
    ("Transactions", ["InitProducerId", "AddPartitionsToTxn", "AddOffsetsToTxn", "EndTxn",
                      "WriteTxnMarkers", "TxnOffsetCommit"]),
    ("Admin", ["CreateTopics", "DeleteTopics", "DeleteRecords", "CreatePartitions",
               "DeleteGroups", "DescribeConfigs", "AlterConfigs",
               "AlterPartitionReassignments", "ListPartitionReassignments"]),
    ("Negotiation/Auth", ["SaslHandshake", "ApiVersions", "SaslAuthenticate"]),
]


def load(name):
    with open(os.path.join(MSG, "message", name)) as f:
        text = re.sub(r"^ *//.*$", "", f.read(), flags=re.MULTILINE)
    return json.loads(text)


def parse_spec(spec):
    if spec is None:
        return None
    spec = spec.strip()
    if spec.endswith("+"):
        return (int(spec[:-1]), None)
    if "-" in spec:
        lo, hi = spec.split("-")
        return (int(lo), int(hi))
    n = int(spec)
    return (n, n)


def covers(gate, version):
    if gate is None:
        return True
    lo, hi = parse_spec(gate)
    if lo is None:
        return True
    if version < lo:
        return False
    if hi is not None and version > hi:
        return False
    return True


def field_sig(f):
    """name:type[gate](null:gate) — the table's delta notation."""
    s = f"{f['name']}:{f['type']}"
    if f.get("versions"):
        s += f"[{f['versions']}]"
    if f.get("nullableVersions"):
        s += f"(null:{f['nullableVersions']})"
    if f.get("mapKey"):
        s += "(key)"
    return s


def api_table(api):
    req = load(f"{api}Request.json")
    maxv = parse_spec(req["validVersions"])
    maxv = maxv[1] if maxv[1] is not None else maxv[0]
    lines = [f"### {api} (API {req['apiKey']}) — v0..v{maxv}\n"]
    lines.append("| Version | Δ vs previous (name:type, per schema) | Status | Commit |")
    lines.append("|---------|----------------------------------------|--------|--------|")
    prev = []
    for v in range(maxv + 1):
        cur = [field_sig(f) for f in collect_fields(req["fields"], v)]
        rpath = os.path.join(os.path.dirname(os.path.abspath(__file__)), "message", f"{api}Response.json")
        if os.path.exists(rpath):
            resp = load(f"{api}Response.json")
            cur += [field_sig(f) for f in collect_fields(resp["fields"], v)]
        cur_set = set(cur)
        prev_set = set(prev)
        if v == 0:
            base_fields = [f["name"] for f in collect_fields(req["fields"], 0)]
            lines.append(f"| v0 | base ({len(base_fields)} fields): {', '.join(base_fields)} | ☐ | |")
        else:
            added = [s for s in cur if s not in prev_set]
            removed = [s for s in prev if s not in cur_set]
            if not added and not removed:
                lines.append(f"| v{v} | unchanged | ☐ | |")
            else:
                parts = []
                if added:
                    parts.append("+ " + ", ".join(added))
                if removed:
                    parts.append("− " + ", ".join(removed))
                lines.append(f"| v{v} | " + "<br>".join(parts) + " | ☐ | |")
        prev = cur
    lines.append("")
    return "\n".join(lines)


def collect_fields(fields, version):
    """Fields (incl. nested) present at `version`, with their FULL signature.

    Used for BOTH request and response field trees; the Δ in a table row is
    taken over the union of the two (a version sub-task implements both sides).
    """
    out = []

    def walk(fs):
        for f in fs:
            if not covers(f.get("versions"), version):
                continue
            out.append(f)
            if "fields" in f:
                walk(f["fields"])
    walk(fields)
    return out


def main():
    out = []
    for cat, apis in CATEGORIES:
        out.append(f"## {cat}\n")
        for api in apis:
            p = os.path.join(MSG, "message", f"{api}Request.json")
            if not os.path.exists(p):
                out.append(f"### {api} — MISSING SPEC\n")
                continue
            out.append(api_table(api))
    sys.stdout.write("\n".join(out))


if __name__ == "__main__":
    main()
