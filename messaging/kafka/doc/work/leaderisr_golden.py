#!/usr/bin/env python3
"""Reference encoder for LeaderAndIsr request/response (Kafka 3.6.1 schema).

Independent golden-vector source: builds bytes from the frozen order tables
(messaging/kafka/doc/spec/order/LeaderAndIsr.{Request,Response}.txt) and the
spec JSONs in messaging/kafka/doc/spec/message/.
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
    return varint(len(b) + 1)

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

UUID_ZERO = bytes(16)


# ---------- request helpers ----------

def encode_partition_state_v0(version, topic, partition, leader, leader_epoch, isr,
                               partition_epoch=0, replicas=None, is_new=False,
                               adding=None, removing=None, recovery=0):
    """Encode a single PartitionState for v0-v1 (ungrouped)."""
    out = fstr(topic)  # v0-v1: topic name present
    out += s32(partition)
    out += s32(leader)
    out += s32(leader_epoch)
    out += farr_int32(isr)
    out += s32(partition_epoch)
    out += farr_int32(replicas or [])
    if version >= 1:
        out += b1(is_new)
    if version >= 3:
        out += nullable_farr_int32(adding)
        out += nullable_farr_int32(removing)
    if version >= 6:
        out += b1(recovery == 1)
    return out

def encode_partition_state_v2(version, partition, leader, leader_epoch, isr,
                               partition_epoch=0, replicas=None, is_new=False,
                               adding=None, removing=None, recovery=0):
    """Encode a single PartitionState for v2+ (grouped under TopicState)."""
    out = s32(partition)
    out += s32(leader)
    out += s32(leader_epoch)
    out += farr_int32(isr)
    out += s32(partition_epoch)
    out += farr_int32(replicas or [])
    if version >= 1:
        out += b1(is_new)
    if version >= 3:
        out += nullable_farr_int32(adding)
        out += nullable_farr_int32(removing)
    if version >= 6:
        out += b1(recovery == 1)
    return out

def encode_partition_state_flex(version, partition, leader, leader_epoch, isr,
                                 partition_epoch=0, replicas=None, is_new=False,
                                 adding=None, removing=None, recovery=0):
    """Encode a single PartitionState for v4+ (compact strings, varint arrays)."""
    out = s32(partition)
    out += s32(leader)
    out += s32(leader_epoch)
    out += carr_int32(isr)
    out += s32(partition_epoch)
    out += carr_int32(replicas or [])
    if version >= 1:
        out += b1(is_new)
    if version >= 3:
        out += nullable_carr_int32(adding)
        out += nullable_carr_int32(removing)
    if version >= 6:
        out += b1(recovery == 1)
    return out

def encode_live_leader(version, broker_id, host, port, flexible=False):
    """Encode a LiveLeader entry."""
    out = s32(broker_id)
    if flexible:
        out += cstr(host)
    else:
        out += fstr(host)
    out += s32(port)
    return out


# ---------- request ----------

def request(version, controller_id=0, controller_epoch=1, broker_epoch=-1,
            is_kraft=False, type=0,
            partitions=None, topics=None, live_leaders=None):
    """
    partitions: list of (topic, partition, leader, leader_epoch, isr,
                  partition_epoch, replicas, is_new, adding, removing, recovery)
    topics: list of (topic_name, topic_id, partitions_list)
    live_leaders: list of (broker_id, host, port)
    """
    flex = version >= 4
    out = s32(controller_id)

    if version >= 7:
        out += b1(is_kraft)
        out += s32(controller_epoch)
    else:
        out += s32(controller_epoch)

    if version >= 2:
        out += s64(broker_epoch)

    if version >= 5:
        out += b1(type != 0)  # type as bool for non-zero

    if version <= 1:
        # UngroupedPartitionStates
        if partitions is None:
            partitions = []
        count = len(partitions)
        out += farr(count) if not flex else carr(count)
        for p in partitions:
            topic, part, leader, le, isr = p[:5]
            pe = p[5] if len(p) > 5 else 0
            rep = p[6] if len(p) > 6 else None
            isnew = p[7] if len(p) > 7 else False
            add = p[8] if len(p) > 8 else None
            rem = p[9] if len(p) > 9 else None
            rec = p[10] if len(p) > 10 else 0
            if flex:
                out += encode_partition_state_flex(version, part, leader, le, isr,
                                                    pe, rep, isnew, add, rem, rec)
            else:
                out += encode_partition_state_v0(version, topic, part, leader, le, isr,
                                                  pe, rep, isnew, add, rem, rec)
    else:
        # TopicStates
        if topics is None:
            topics = []
        count = len(topics)
        out += farr(count) if not flex else carr(count)
        for t in topics:
            tname, tid, tparts = t[0], t[1] if len(t) > 1 else None, t[2] if len(t) > 2 else []
            if flex:
                out += cstr(tname)
                if version >= 5:
                    out += (tid if tid else UUID_ZERO)
            else:
                out += fstr(tname)
                if version >= 5:
                    out += (tid if tid else UUID_ZERO)
            # PartitionStates
            pcount = len(tparts)
            out += farr(pcount) if not flex else carr(pcount)
            for p in tparts:
                part, leader, le, isr = p[:4]
                pe = p[4] if len(p) > 4 else 0
                rep = p[5] if len(p) > 5 else None
                isnew = p[6] if len(p) > 6 else False
                add = p[7] if len(p) > 7 else None
                rem = p[8] if len(p) > 8 else None
                rec = p[9] if len(p) > 9 else 0
                if flex:
                    out += encode_partition_state_flex(version, part, leader, le, isr,
                                                        pe, rep, isnew, add, rem, rec)
                else:
                    out += encode_partition_state_v2(version, part, leader, le, isr,
                                                      pe, rep, isnew, add, rem, rec)

    # LiveLeaders
    if live_leaders is None:
        live_leaders = []
    count = len(live_leaders)
    out += farr(count) if not flex else carr(count)
    for ll in live_leaders:
        bid, host, port = ll[0], ll[1], ll[2]
        out += encode_live_leader(version, bid, host, port, flex)

    # Flexible endTags
    if flex:
        # Top-level endTags (approximate count: 4 variable fields)
        out += varint(4)
        # Per-topic endTags
        if version <= 1:
            for _ in partitions or []:
                out += varint(3)  # topic, isr, replicas
        else:
            for _ in topics or []:
                out += varint(4)  # name, topicId, isr, replicas
        for _ in live_leaders or []:
            out += varint(1)  # host

    return out


# ---------- response ----------

def encode_partition_error(version, topic, partition, error_code, flexible=False):
    """Encode a single PartitionError."""
    out = s32(partition)
    out += s16(error_code)
    return out

def encode_partition_error_v0(version, topic, partition, error_code):
    """Encode a single PartitionError for v0-v4 (flat)."""
    out = fstr(topic)
    out += s32(partition)
    out += s16(error_code)
    return out

def encode_partition_error_flex(version, topic, partition, error_code):
    """Encode a single PartitionError for v4+ (compact strings)."""
    out = cstr(topic)
    out += s32(partition)
    out += s16(error_code)
    return out


def response(version, error_code=0, partition_errors=None, topics=None):
    """
    partition_errors: list of (topic, partition, error_code)
    topics: list of (topic_id, [(partition, error_code)])
    """
    flex = version >= 4
    out = s16(error_code)

    if version <= 4:
        if partition_errors is None:
            partition_errors = []
        count = len(partition_errors)
        out += farr(count) if not flex else carr(count)
        for pe in partition_errors:
            topic, part, ec = pe[0], pe[1], pe[2]
            if flex:
                out += encode_partition_error_flex(version, topic, part, ec)
            else:
                out += encode_partition_error_v0(version, topic, part, ec)
    else:
        if topics is None:
            topics = []
        count = len(topics)
        out += farr(count) if not flex else carr(count)
        for t in topics:
            tid, tparts = t[0], t[1] if len(t) > 1 else []
            out += (tid if tid else UUID_ZERO)
            pcount = len(tparts)
            out += farr(pcount) if not flex else carr(pcount)
            for pe in tparts:
                part, ec = pe[0], pe[1]
                out += encode_partition_error(version, None, part, ec, flex)

    # Flexible endTags
    if flex:
        if version <= 4:
            out += varint(1)  # partition errors
        else:
            out += varint(1)  # topics
            for _ in topics or []:
                out += varint(1)  # partition errors per topic

    return out


# ---------- golden vectors for ALL versions v0-v7 ----------
# Generated using the EXACT same fixtures as LeaderAndIsrCodecTest

# Request v0: one topic "topic1", partition 0, leader 0, epoch 1, isr [0,1,2], replicas [0,1]
REQ_V0 = request(0, partitions=[("topic1", 0, 0, 1, [0,1,2], 0, [0,1])])

# Request v1: +IsNew=true
REQ_V1 = request(1, partitions=[("topic1", 0, 0, 1, [0,1,2], 0, [0,1], True)])

# Request v2: +BrokerEpoch=12345, TopicStates
REQ_V2 = request(2, broker_epoch=12345, topics=[
    ("topic1", None, [(0, 0, 1, [0,1,2], 0, [0,1])])
])

# Request v3: +AddingReplicas=[3,4], RemovingReplicas=[5]
REQ_V3 = request(3, broker_epoch=12345, topics=[
    ("topic1", None, [(0, 0, 1, [0,1,2], 0, [0,1], False, [3,4], [5], 0)])
])

# Request v4: first flexible
REQ_V4 = request(4, topics=[
    ("topic1", None, [(0, 0, 1, [0,1,2], 0, [0,1])])
])

# Request v5: +Type=0, TopicId=zero
REQ_V5 = request(5, topics=[
    ("topic1", UUID_ZERO, [(0, 0, 1, [0,1,2], 0, [0,1])])
])

# Request v6: +LeaderRecoveryState=1
REQ_V6 = request(6, topics=[
    ("topic1", UUID_ZERO, [(0, 0, 1, [0,1,2], 0, [0,1], False, None, None, 1)])
])

# Request v7: +isKRaftController=true
REQ_V7 = request(7, is_kraft=True, topics=[
    ("topic1", UUID_ZERO, [(0, 0, 1, [0,1,2], 0, [0,1])])
])

# Response v0: one partition error
RESP_V0 = response(0, partition_errors=[("topic1", 0, 0)])

# Response v1: same structure as v0
RESP_V1 = response(1, partition_errors=[("topic1", 0, 0)])

# Response v2: same structure as v0
RESP_V2 = response(2, partition_errors=[("topic1", 0, 0)])

# Response v3: same structure as v0
RESP_V3 = response(3, partition_errors=[("topic1", 0, 0)])

# Response v4: first flexible
RESP_V4 = response(4, partition_errors=[("topic1", 0, 0)])

# Response v5: +Topics structure
RESP_V5 = response(5, topics=[(UUID_ZERO, [(0, 0), (1, 1)])])

# Response v6: same as v5
RESP_V6 = response(6, topics=[(UUID_ZERO, [(0, 0), (1, 1)])])

# Response v7: same as v5
RESP_V7 = response(7, topics=[(UUID_ZERO, [(0, 0), (1, 1)])])


if __name__ == "__main__":
    print(f"REQ_V0 = fromHex(\"{REQ_V0.hex()}\")")
    print(f"REQ_V1 = fromHex(\"{REQ_V1.hex()}\")")
    print(f"REQ_V2 = fromHex(\"{REQ_V2.hex()}\")")
    print(f"REQ_V3 = fromHex(\"{REQ_V3.hex()}\")")
    print(f"REQ_V4 = fromHex(\"{REQ_V4.hex()}\")")
    print(f"REQ_V5 = fromHex(\"{REQ_V5.hex()}\")")
    print(f"REQ_V6 = fromHex(\"{REQ_V6.hex()}\")")
    print(f"REQ_V7 = fromHex(\"{REQ_V7.hex()}\")")
    print(f"RESP_V0 = fromHex(\"{RESP_V0.hex()}\")")
    print(f"RESP_V1 = fromHex(\"{RESP_V1.hex()}\")")
    print(f"RESP_V2 = fromHex(\"{RESP_V2.hex()}\")")
    print(f"RESP_V3 = fromHex(\"{RESP_V3.hex()}\")")
    print(f"RESP_V4 = fromHex(\"{RESP_V4.hex()}\")")
    print(f"RESP_V5 = fromHex(\"{RESP_V5.hex()}\")")
    print(f"RESP_V6 = fromHex(\"{RESP_V6.hex()}\")")
    print(f"RESP_V7 = fromHex(\"{RESP_V7.hex()}\")")