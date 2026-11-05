#!/usr/bin/env python3
"""Reference encoder for UpdateMetadata request/response (Kafka 3.6.1 schema).

Generates golden byte vectors for all versions v0-v8.
Based on frozen order tables: doc/spec/order/UpdateMetadata.{Request,Response}.txt
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

def nullable_farr_int32(xs):
    """Non-flexible: int32 count, -1 = null."""
    if xs is None:
        return s32(-1)
    return farr_int32(xs)

def nullable_carr_int32(xs):
    """Flexible: varint(0) = null/empty."""
    if xs is None or len(xs) == 0:
        return varint(0)
    return carr_int32(xs)

# ---------- request helpers ----------

def encode_partition_state(version, topic, partition, controller_epoch, leader, leader_epoch, isr,
                           zk_version=0, replicas=None, offline=None):
    """Encode a single PartitionState for any version."""
    flex = version >= 6
    out = b""
    if version <= 4:
        out += (fstr(topic) if not flex else cstr(topic))
    out += s32(partition)
    out += s32(controller_epoch)
    out += s32(leader)
    out += s32(leader_epoch)
    out += (carr_int32(isr) if flex else farr_int32(isr))
    out += s32(zk_version)
    out += (carr_int32(replicas or []) if flex else farr_int32(replicas or []))
    if version >= 4:
        out += (nullable_carr_int32(offline) if flex else nullable_farr_int32(offline))
    if flex:
        out += varint(0)  # endTags
    return out

def encode_broker(version, broker_id, host, port):
    """Encode a LiveBroker entry."""
    flex = version >= 6
    out = s32(broker_id)
    out += (cstr(host) if flex else fstr(host))
    out += s32(port)
    if flex:
        out += varint(0)  # endTags
    return out

# ---------- request ----------

def request(version, controller_id=0, controller_epoch=1, broker_epoch=-1,
            is_kraft=False, type=0,
            partitions=None, topics=None, live_brokers=None):
    """
    partitions: list of (topic, partition, controller_epoch, leader, leader_epoch, isr,
                  zk_version, replicas, offline)
    topics: list of (topic_name, partitions_list)
    live_brokers: list of (broker_id, host, port)
    """
    flex = version >= 6
    out = s32(controller_id)
    if version >= 8:
        out += b1(is_kraft)
        out += bytes([type])
    out += s32(controller_epoch)
    if version >= 5:
        out += s64(broker_epoch)
    
    if version <= 4:
        # UngroupedPartitionStates
        if partitions is None:
            partitions = []
        count = len(partitions)
        out += (carr(count) if flex else farr(count))
        for p in partitions:
            topic, part, ce, leader, le, isr = p[0], p[1], p[2], p[3], p[4], p[5]
            zk = p[6] if len(p) > 6 else 0
            rep = p[7] if len(p) > 7 else None
            off = p[8] if len(p) > 8 else None
            out += encode_partition_state(version, topic, part, ce, leader, le, isr, zk, rep, off)
    else:
        # TopicStates
        if topics is None:
            topics = []
        count = len(topics)
        out += (carr(count) if flex else farr(count))
        for t in topics:
            tname, tparts = t[0], t[1] if len(t) > 1 else []
            out += (cstr(tname) if flex else fstr(tname))
            # PartitionStates
            pcount = len(tparts)
            out += (carr(pcount) if flex else farr(pcount))
            for p in tparts:
                part, ce, leader, le, isr = p[0], p[1], p[2], p[3], p[4]
                zk = p[5] if len(p) > 5 else 0
                rep = p[6] if len(p) > 6 else None
                off = p[7] if len(p) > 7 else None
                out += encode_partition_state(version, None, part, ce, leader, le, isr, zk, rep, off)
            if flex:
                out += varint(0)  # per-topic endTags
    
    # LiveBrokers
    if live_brokers is None:
        live_brokers = []
    count = len(live_brokers)
    out += (carr(count) if flex else farr(count))
    for lb in live_brokers:
        bid, host, port = lb[0], lb[1], lb[2]
        out += encode_broker(version, bid, host, port)
    
    if flex:
        out += varint(0)  # top-level endTags
    
    return out

# ---------- response ----------

def response(version, error_code=0):
    """Encode UpdateMetadata response."""
    out = s16(error_code)
    return out

# ---------- test vectors ----------

if __name__ == "__main__":
    # Request vectors
    print("// Request golden vectors")
    # v0: flat partitions
    req = request(0, 0, 1, partitions=[
        ("topic1", 0, 1, 0, 1, [0, 1, 2], 0, [0, 1], None),
        ("topic1", 1, 1, 1, 1, [0, 1], 0, [0, 1], None),
    ], live_brokers=[(0, "host1", 9092)])
    print(f'REQ_V0 = fromHex("{req.hex()}");')
    
    # v4: flat partitions with offline
    req = request(4, 0, 1, partitions=[
        ("topic1", 0, 1, 0, 1, [0, 1, 2], 0, [0, 1], [2]),
    ], live_brokers=[(0, "host1", 9092)])
    print(f'REQ_V4 = fromHex("{req.hex()}");')
    
    # v5: topic states
    req = request(5, 0, 1, 0x3039, topics=[
        ("topic1", [(0, 1, 0, 1, [0, 1, 2], 0, [0, 1], None)]),
    ], live_brokers=[(0, "host1", 9092)])
    print(f'REQ_V5 = fromHex("{req.hex()}");')
    
    # v8: with isKRaftController and Type
    req = request(8, 0, 1, 0x3039, is_kraft=True, type=1, topics=[
        ("topic1", [(0, 1, 0, 1, [0, 1, 2], 0, [0, 1], None)]),
    ], live_brokers=[(0, "host1", 9092)])
    print(f'REQ_V8 = fromHex("{req.hex()}");')
    
    # Response vectors
    print("// Response golden vectors")
    for v in range(9):
        resp = response(v, 0)
        print(f'RESP_V{v} = fromHex("{resp.hex()}");')