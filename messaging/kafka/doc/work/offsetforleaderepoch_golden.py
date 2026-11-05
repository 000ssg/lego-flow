#!/usr/bin/env python3
"""Reference encoder for OffsetForLeaderEpoch request/response (Kafka 3.6.1 schema).

Generates golden byte vectors for all versions v0-v4.
Based on frozen order tables: doc/spec/order/OffsetForLeaderEpoch.{Request,Response}.txt
"""
import struct, varint, json

def fromHex(s):
    return bytes.fromhex(s)

def toHex(b):
    return b.hex()

def writeCompactString(buf, s):
    if s is None:
        varint.writeVarint(0, buf)
        return
    b = s.encode('utf-8')
    varint.writeVarint(len(b) + 1, buf)
    buf.write(b)

def readCompactString(buf):
    n = varint.readVarint(buf)
    if n == 0:
        return None
    return buf.read(n - 1).decode('utf-8')

def writeCompactBytes(buf, b):
    if b is None:
        varint.writeVarint(0, buf)
        return
    varint.writeVarint(len(b) + 1, buf)
    buf.write(b)

def writeIntList(buf, lst):
    varint.writeVarint(len(lst), buf)
    for x in lst:
        varint.writeVarint(x, buf)

def writeEndTags(buf, count):
    for _ in range(count):
        varint.writeVarint(0, buf)

# ── Request ──────────────────────────────────────────────────────────────────

def encodeRequest(version, cluster_id, topics):
    import io
    buf = io.BytesIO()
    writeCompactString(buf, cluster_id)
    varint.writeVarint(len(topics), buf)
    for topic_name, partition_indexes in topics:
        writeCompactString(buf, topic_name)
        writeIntList(buf, partition_indexes)
        if version >= 4:
            writeEndTags(buf, 1)
    if version >= 4:
        writeEndTags(buf, 1)
    return buf.getvalue()

# ── Response ─────────────────────────────────────────────────────────────────

def encodeResponse(version, error_code, topics):
    import io
    buf = io.BytesIO()
    buf.write(struct.pack('>h', error_code))
    varint.writeVarint(len(topics), buf)
    for topic_name, epoch_end_offsets in topics:
        writeCompactString(buf, topic_name)
        varint.writeVarint(len(epoch_end_offsets), buf)
        for partition, end_offset, err_code in epoch_end_offsets:
            varint.writeVarint(partition, buf)
            buf.write(struct.pack('>q', end_offset))
            varint.writeVarint(err_code, buf)
        if version >= 4:
            writeEndTags(buf, 1)
    if version >= 4:
        writeEndTags(buf, 1)
    return buf.getvalue()

# ── Test vectors ─────────────────────────────────────────────────────────────

# v0: simple request
req = encodeRequest(0, None, [
    ("topic1", [0, 1, 2]),
])
print(f"REQ_V0 = fromHex(\"{toHex(req)}\");")

# v3: with clusterId
req = encodeRequest(3, "cluster", [
    ("topic1", [0, 1]),
    ("topic2", [0]),
])
print(f"REQ_V3 = fromHex(\"{toHex(req)}\");")

# v4: flexible encoding
req = encodeRequest(4, "cluster", [
    ("topic1", [0, 1, 2]),
])
print(f"REQ_V4 = fromHex(\"{toHex(req)}\");")

# Response v0
resp = encodeResponse(0, 0, [
    ("topic1", [(0, 100, 0), (1, 200, 0)]),
])
print(f"RESP_V0 = fromHex(\"{toHex(resp)}\");")

# Response v4
resp = encodeResponse(4, 1, [
    ("topic1", [(0, 100, 0)]),
])
print(f"RESP_V4 = fromHex(\"{toHex(resp)}\");")