#!/usr/bin/env python3
"""Reference encoder for StopReplica request/response (Kafka 3.6.1 schema).

Generates golden byte vectors for all versions v0-v4.
"""
import struct

# ---------- primitives ----------

def varint(n):
    """Unsigned LEB128 varint."""
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)

def s16(x): return struct.pack(">h", x)
def s32(x): return struct.pack(">i", x)
def s64(x): return struct.pack(">q", x)
def b1(x): return bytes([1 if x else 0])

def fstr(s):
    """Fixed string: int16 length + UTF-8 (length -1 = null)."""
    if s is None:
        return s16(-1)
    b = s.encode("utf-8")
    return s16(len(b)) + b

def cstr(s):
    """Compact string: varint(len+1)+UTF-8, varint(0)=null."""
    if s is None:
        return varint(0)
    b = s.encode("utf-8")
    return varint(len(b) + 1) + b

def farr(n): return s32(n)
def carr(n): return varint(n + 1)

def farr_int32(xs):
    out = s32(len(xs))
    for x in xs:
        out += s32(x)
    return out

def carr_int32(xs):
    out = varint(len(xs) + 1)
    for x in xs:
        out += s32(x)
    return out

# ---------- request ----------

def request(version, controller_id=0, controller_epoch=1, broker_epoch=-1,
            is_kraft=False, delete_partitions=False,
            partitions=None, topics=None, topic_states=None):
    """
    partitions: list of (topic, partition) — v0 only
    topics: list of (topic_name, [partition_indexes]) — v1-v2
    topic_states: list of (topic_name, [(partition, leader_epoch, delete_partition)]) — v3+
    """
    flex = version >= 2
    out = s32(controller_id) + s32(controller_epoch)
    if version >= 1:
        out += s64(broker_epoch)
    if version >= 4:
        out += b1(is_kraft)
    if version <= 2:
        out += b1(delete_partitions)

    if version == 0:
        if partitions is None:
            partitions = []
        out += farr(len(partitions))
        for p in partitions:
            topic, part = p[0], p[1]
            out += fstr(topic) + s32(part)
    elif version <= 2:
        if topics is None:
            topics = []
        out += (carr(len(topics)) if flex else farr(len(topics)))
        for t in topics:
            tname, pindexes = t[0], t[1]
            out += (cstr(tname) if flex else fstr(tname))
            out += (carr_int32(pindexes) if flex else farr_int32(pindexes))
    else:
        if topic_states is None:
            topic_states = []
        out += (carr(len(topic_states)) if flex else farr(len(topic_states)))
        for ts in topic_states:
            tname, pstates = ts[0], ts[1]
            out += (cstr(tname) if flex else fstr(tname))
            out += (carr(len(pstates)) if flex else farr(len(pstates)))
            for ps in pstates:
                part, le, dp = ps[0], ps[1], ps[2]
                out += s32(part) + s32(le) + b1(dp)

    return out

# ---------- response ----------

def response(version, error_code=0, partition_errors=None):
    """
    partition_errors: list of (topic, partition, error_code)
    """
    flex = version >= 2
    out = s16(error_code)
    if partition_errors is None:
        partition_errors = []
    out += (carr(len(partition_errors)) if flex else farr(len(partition_errors)))
    for pe in partition_errors:
        topic, part, ec = pe[0], pe[1], pe[2]
        out += fstr(topic) + s32(part) + s16(ec)
    return out

# ---------- golden vectors ----------

if __name__ == "__main__":
    # Request golden vectors
    REQ_V0 = request(0, 0, 1, delete_partitions=True,
                     partitions=[("topic1", 0), ("topic1", 1)])
    print(f'REQ_V0 = fromHex("{REQ_V0.hex()}")')

    REQ_V1 = request(1, 0, 1, 0x3039,
                     topics=[("topic1", [0, 1])])
    print(f'REQ_V1 = fromHex("{REQ_V1.hex()}")')

    REQ_V2 = request(2, 0, 1, 0x3039, delete_partitions=True,
                     topics=[("topic1", [0, 1])])
    print(f'REQ_V2 = fromHex("{REQ_V2.hex()}")')

    REQ_V3 = request(3, 0, 1, 0x3039,
                     topic_states=[("topic1", [(0, 1, False), (1, 1, True)])])
    print(f'REQ_V3 = fromHex("{REQ_V3.hex()}")')

    REQ_V4 = request(4, 1, 1, 0x3039, is_kraft=True,
                     topic_states=[("topic1", [(0, 1, False)])])
    print(f'REQ_V4 = fromHex("{REQ_V4.hex()}")')

    # Response golden vectors
    RESP_V0 = response(0, 0, [("topic1", 0, 0)])
    print(f'RESP_V0 = fromHex("{RESP_V0.hex()}")')

    RESP_V1 = response(1, 0, [("topic1", 0, 0)])
    print(f'RESP_V1 = fromHex("{RESP_V1.hex()}")')

    RESP_V2 = response(2, 0, [("topic1", 0, 0)])
    print(f'RESP_V2 = fromHex("{RESP_V2.hex()}")')

    RESP_V3 = response(3, 0, [("topic1", 0, 0)])
    print(f'RESP_V3 = fromHex("{RESP_V3.hex()}")')

    RESP_V4 = response(4, 0, [("topic1", 0, 0)])
    print(f'RESP_V4 = fromHex("{RESP_V4.hex()}")')
