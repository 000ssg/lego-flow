#!/usr/bin/env python3
"""Reference encoder for Metadata request/response (Kafka 3.6.1 schema).

Independent golden-vector source: builds bytes from the frozen order tables
(messaging/kafka/doc/spec/order/Metadata.{Request,Response}.txt) and the spec
JSONs in messaging/kafka/doc/spec/message/. Output: hex vectors for the
fixed fixture used in MetadataCodecTest.
"""
import struct


# ---------- primitives ----------

def varint(n):
    """Unsigned LEB128 varint, as written by KafkaCodecPrimitives.writeVarint."""
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
def b1(x): return bytes([1 if x else 0])

def fstr(s):
    """Fixed string: int16 length + UTF-8 (length -1 = null)."""
    if s is None:
        return s16(-1)
    b = s.encode("utf-8")
    return s16(len(b)) + b

def cstr(s, nullable=False):
    """Compact string: varint(len+1)+UTF-8, varint(0)=null when nullable."""
    if s is None:
        if not nullable:
            raise ValueError("non-nullable compact string given null")
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

UUID_ZERO = bytes(16)


# ---------- request ----------

def request(version, topics, allow=False, include_cluster=None, include_topic=None):
    """topics: list of names or None (null array, v1+)."""
    flex = version >= 9
    if version < 1:
        if topics is None:
            topics = []  # v0: empty array = all topics (no nullable array)
    if topics is None:
        # v1+ null array marker: legacy int32(-1); flexible varint(0)
        out = farr(-1) if not flex else varint(0)
    else:
        out = farr(len(topics)) if not flex else carr(len(topics))
    for name in (topics or []):
        if version >= 10:
            out += UUID_ZERO  # TopicId (uuid)
        out += cstr(name, nullable=version >= 10) if flex else fstr(name)
        if flex:
            out += varint(0)  # per-topic endTags
    if version >= 4:
        out += b1(allow)
    if 8 <= version <= 10:
        out += b1(include_cluster if include_cluster is not None else False)
    if version >= 8:
        out += b1(include_topic if include_topic is not None else False)
    if flex:
        out += varint(0)  # top-level endTags
    return out


# ---------- response ----------

class R:
    @staticmethod
    def full(version=None, throttle=0, broker_rack=None, cluster=None, controller=-1,
             topic_err=0, topic_name="t1", topic_internal=False, topic_id=UUID_ZERO,
             p_err=0, p_idx=0, leader=7, leader_epoch=-1, replicas=(7,), isrs=(7,),
             offline=(), topic_auth=None, cluster_auth=None):
        flex = version >= 9
        if version >= 3:
            out = s32(throttle)
        else:
            out = b""
        # Brokers: 1 broker
        out += farr(1) if not flex else carr(1)
        out += s32(0)  # NodeId
        out += cstr("host1") if flex else fstr("host1")
        out += s32(9092)  # Port
        if version >= 1:
            rack = broker_rack if broker_rack is not None else None
            out += cstr(rack, nullable=True) if flex else fstr(rack)
        if flex:
            out += varint(0)  # per-broker endTags
        if version >= 2:
            cid = cluster if cluster is not None else None
            out += cstr(cid, nullable=True) if flex else fstr(cid)
        if version >= 1:
            out += s32(controller)
        # Topics: 1 topic
        out += farr(1) if not flex else carr(1)
        out += s16(topic_err)
        out += cstr(topic_name, nullable=version >= 12) if flex else fstr(topic_name)
        if version >= 10:
            out += (topic_id if topic_id is not None else UUID_ZERO)
        if version >= 1:
            out += b1(topic_internal)
        # Partitions: 1 partition
        out += farr(1) if not flex else carr(1)
        out += s16(p_err)
        out += s32(p_idx)
        out += s32(leader)
        if version >= 7:
            out += s32(leader_epoch)
        out += farr_int32(replicas) if not flex else carr_int32(replicas)
        out += farr_int32(isrs) if not flex else carr_int32(isrs)
        if version >= 5:
            off = offline if offline is not None else ()
            out += farr_int32(off) if not flex else carr_int32(off)
        if flex:
            out += varint(0)  # per-partition endTags
        if version >= 8:
            ta = topic_auth if topic_auth is not None else -2147483648
            out += s32(ta)
        if flex:
            out += varint(0)  # per-topic endTags
        if 8 <= version <= 10:
            ca = cluster_auth if cluster_auth is not None else -2147483648
            out += s32(ca)
        if flex:
            out += varint(0)  # top-level endTags
        return out


def main():
    hexes = {}

    # ---- request goldens (all-tasks fixture: topics ["topic1"]) ----
    hexes["REQ_V0"] = request(0, ["topic1"])
    hexes["REQ_V3"] = request(3, ["topic1"])
    hexes["REQ_V4"] = request(4, ["topic1"])  # + AllowAutoTopicCreation(true)
    hexes["REQ_V8"] = request(8, ["topic1"])  # + AllowAuto(true) + IncCluster(false) + IncTopic(false)
    hexes["REQ_V9"] = request(9, ["topic1"])
    hexes["REQ_V10"] = request(10, ["topic1"])
    hexes["REQ_V11"] = request(11, ["topic1"])
    hexes["REQ_V12"] = request(12, ["topic1"])
    hexes["REQ_V0_ALL"] = request(0, None)          # null -> empty array at v0
    hexes["REQ_V1_ALL"] = request(1, None)          # null -> count -1 at v1+
    hexes["REQ_V8_TWOTOPIC"] = request(8, ["alpha", "beta"])

    # ---- response goldens (1 broker 0/host1:9092, 1 topic t1, 1 partition 0/leader 7) ----
    for v in range(13):
        hexes[f"RSP_V{v}"] = R.full(v)
    hexes["RSP_V3_THROTTLED"] = R.full(3, throttle=250)
    hexes["RSP_V8"] = R.full(8)
    hexes["RSP_V8_AUTHS"] = R.full(8, topic_auth=4, cluster_auth=16)

    for k in sorted(hexes):
        h = hexes[k].hex()
        print(f"    /** {k} */")
        print(f"    private static final byte[] {k} = fromHex(\"{h}\");")
    print()
    print("lengths:")
    for k in sorted(hexes):
        print(f"  {k}: {len(hexes[k])} bytes")


if __name__ == "__main__":
    main()
