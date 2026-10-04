# LISTOFFSETS — SETTLED EVALUATION LEDGER (re-usable; do NOT re-derive)
Created: 2026-09-30, session 459fca96. ALL downstream generation must use ONLY this file.

## Authoritative source-of-truth (FINAL, verified live 2026-09-30)
- messaging/kafka/doc/spec/message/ListOffsetsRequest.json + ListOffsetsResponse.json
  == Apache Kafka 3.6.1 clients/src/main/resources/common/message/*.json (raw.githubusercontent, byte-equivalent).
- This OVERRIDES: (a) plan-matrix rows in doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md (wrong),
  (b) the 832-line codec/ListOffsetsCodec.java (hallucinated artifact of a stopped bg agent:
  contains MaxWaitMs, ReplicaEpoch, CurrentTimestamp, MaxNumOffsets, RackId — in NEITHER spec nor real Kafka).
- Frozen per-version order tables (generated from the spec JSON by committed gen_kafka.py):
  doc/spec/order/ListOffsets.Request.txt, doc/spec/order/ListOffsets.Response.txt — below, verbatim.

## gen_kafka.py usage
gen_kafka.py — spec-first generator for Kafka codec version sub-tasks.

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



## FROZEN ORDER TABLE — REQUEST (verbatim)
ListOffsets Request — field processing order (binding; spec 3.6.1) — flexible encoding from v6
wire order by layout (equal run = byte-identical field layout):
  v0 -> ReplicaId:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp:(Partitions)MaxNumOffsets?
  v1 -> ReplicaId:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp
  v2-v3 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp
  v4-v5 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)CurrentLeaderEpoch?:(Partitions)Timestamp
  v6-v8 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)CurrentLeaderEpoch?:(Partitions)Timestamp|flex
field lines (fixed section first, trailing tagged section last, ascending tag):
  v0:
  v0: fixed: ReplicaId(int32), Topics([]ListOffsetsTopic) || tagged: ∅
  v1:
  v1: fixed: ReplicaId(int32), Topics([]ListOffsetsTopic) || tagged: ∅
  v2-v3:
  v2: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v3: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v4-v5:
  v4: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v5: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v6-v8:
  v6: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅
  v7: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅
  v8: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅

## FROZEN ORDER TABLE — RESPONSE (verbatim)
ListOffsets Response — field processing order (binding; spec 3.6.1) — flexible encoding from v6
wire order by layout (equal run = byte-identical field layout):
  v0 -> Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)OldStyleOffsets?
  v1 -> Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?
  v2-v3 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?
  v4-v5 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?:(Partitions)LeaderEpoch?
  v6-v8 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?:(Partitions)LeaderEpoch?|flex
field lines (fixed section first, trailing tagged section last, ascending tag):
  v0:
  v0: fixed: Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v1:
  v1: fixed: Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v2-v3:
  v2: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v3: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v4-v5:
  v4: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v5: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v6-v8:
  v6: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅
  v7: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅
  v8: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅

## gen_kafka.py order output — REQUEST (verbatim)
ListOffsets Request — field processing order (binding; spec 3.6.1) — flexible encoding from v6
wire order by layout (equal run = byte-identical field layout):
  v0 -> ReplicaId:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp:(Partitions)MaxNumOffsets?
  v1 -> ReplicaId:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp
  v2-v3 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)Timestamp
  v4-v5 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)CurrentLeaderEpoch?:(Partitions)Timestamp
  v6-v8 -> ReplicaId:IsolationLevel?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)CurrentLeaderEpoch?:(Partitions)Timestamp|flex
field lines (fixed section first, trailing tagged section last, ascending tag):
  v0:
  v0: fixed: ReplicaId(int32), Topics([]ListOffsetsTopic) || tagged: ∅
  v1:
  v1: fixed: ReplicaId(int32), Topics([]ListOffsetsTopic) || tagged: ∅
  v2-v3:
  v2: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v3: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v4-v5:
  v4: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v5: fixed: ReplicaId(int32), IsolationLevel(int8), Topics([]ListOffsetsTopic) || tagged: ∅
  v6-v8:
  v6: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅
  v7: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅
  v8: fixed: ReplicaId(int32) [flex], IsolationLevel(int8) [flex], Topics([]ListOffsetsTopic) [flex] || tagged: ∅


## gen_kafka.py order output — RESPONSE (verbatim)
ListOffsets Response — field processing order (binding; spec 3.6.1) — flexible encoding from v6
wire order by layout (equal run = byte-identical field layout):
  v0 -> Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)OldStyleOffsets?
  v1 -> Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?
  v2-v3 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?
  v4-v5 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?:(Partitions)LeaderEpoch?
  v6-v8 -> ThrottleTimeMs?:Topics:(Topics)Name:(Topics)Partitions:(Partitions)PartitionIndex:(Partitions)ErrorCode:(Partitions)Timestamp?:(Partitions)Offset?:(Partitions)LeaderEpoch?|flex
field lines (fixed section first, trailing tagged section last, ascending tag):
  v0:
  v0: fixed: Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v1:
  v1: fixed: Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v2-v3:
  v2: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v3: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v4-v5:
  v4: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v5: fixed: ThrottleTimeMs(int32), Topics([]ListOffsetsTopicResponse) || tagged: ∅
  v6-v8:
  v6: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅
  v7: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅
  v8: fixed: ThrottleTimeMs(int32) [flex], Topics([]ListOffsetsTopicResponse) [flex] || tagged: ∅


## AUTHORITATIVE SPEC JSON — REQUEST (verbatim, == live 3.6.1)
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

{
  "apiKey": 2,
  "type": "request",
  "listeners": ["zkBroker", "broker"],
  "name": "ListOffsetsRequest",
  // Version 1 removes MaxNumOffsets.  From this version forward, only a single
  // offset can be returned.
  //
  // Version 2 adds the isolation level, which is used for transactional reads.
  //
  // Version 3 is the same as version 2.
  //
  // Version 4 adds the current leader epoch, which is used for fencing.
  //
  // Version 5 is the same as version 4.
  //
  // Version 6 enables flexible versions.
  //
  // Version 7 enables listing offsets by max timestamp (KIP-734).
  //
  // Version 8 enables listing offsets by local log start offset (KIP-405).
  "validVersions": "0-8",
  "flexibleVersions": "6+",
  "fields": [
    { "name": "ReplicaId", "type": "int32", "versions": "0+", "entityType": "brokerId",
      "about": "The broker ID of the requester, or -1 if this request is being made by a normal consumer." },
    { "name": "IsolationLevel", "type": "int8", "versions": "2+",
      "about": "This setting controls the visibility of transactional records. Using READ_UNCOMMITTED (isolation_level = 0) makes all records visible. With READ_COMMITTED (isolation_level = 1), non-transactional and COMMITTED transactional records are visible. To be more concrete, READ_COMMITTED returns all data from offsets smaller than the current LSO (last stable offset), and enables the inclusion of the list of aborted transactions in the result, which allows consumers to discard ABORTED transactional records" },
    { "name": "Topics", "type": "[]ListOffsetsTopic", "versions": "0+",
      "about": "Each topic in the request.", "fields": [
      { "name": "Name", "type": "string", "versions": "0+", "entityType": "topicName",
        "about": "The topic name." },
      { "name": "Partitions", "type": "[]ListOffsetsPartition", "versions": "0+",
        "about": "Each partition in the request.", "fields": [
        { "name": "PartitionIndex", "type": "int32", "versions": "0+",
          "about": "The partition index." },
        { "name": "CurrentLeaderEpoch", "type": "int32", "versions": "4+", "default": "-1", "ignorable": true,
          "about": "The current leader epoch." },
        { "name": "Timestamp", "type": "int64", "versions": "0+",
          "about": "The current timestamp." },
        { "name": "MaxNumOffsets", "type": "int32", "versions": "0", "default": "1",
          "about": "The maximum number of offsets to report." }
      ]}
    ]}
  ]
}


## AUTHORITATIVE SPEC JSON — RESPONSE (verbatim, == live 3.6.1)
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

{
  "apiKey": 2,
  "type": "response",
  "name": "ListOffsetsResponse",
  // Version 1 removes the offsets array in favor of returning a single offset.
  // Version 1 also adds the timestamp associated with the returned offset.
  //
  // Version 2 adds the throttle time.
  //
  // Starting in version 3, on quota violation, brokers send out responses before throttling.
  //
  // Version 4 adds the leader epoch, which is used for fencing.
  //
  // Version 5 adds a new error code, OFFSET_NOT_AVAILABLE.
  //
  // Version 6 enables flexible versions.
  //
  // Version 7 is the same as version 6 (KIP-734).
  //
  // Version 8 enables listing offsets by local log start offset.
  // This is the earliest log start offset in the local log. (KIP-405).
  "validVersions": "0-8",
  "flexibleVersions": "6+",
  "fields": [
    { "name": "ThrottleTimeMs", "type": "int32", "versions": "2+", "ignorable": true,
      "about": "The duration in milliseconds for which the request was throttled due to a quota violation, or zero if the request did not violate any quota." },
    { "name": "Topics", "type": "[]ListOffsetsTopicResponse", "versions": "0+",
      "about": "Each topic in the response.", "fields": [
      { "name": "Name", "type": "string", "versions": "0+", "entityType": "topicName",
        "about": "The topic name" },
      { "name": "Partitions", "type": "[]ListOffsetsPartitionResponse", "versions": "0+",
        "about": "Each partition in the response.", "fields": [
        { "name": "PartitionIndex", "type": "int32", "versions": "0+",
          "about": "The partition index." },
        { "name": "ErrorCode", "type": "int16", "versions": "0+",
          "about": "The partition error code, or 0 if there was no error." },
        { "name": "OldStyleOffsets", "type": "[]int64", "versions": "0", "ignorable": false,
          "about": "The result offsets." },
        { "name": "Timestamp", "type": "int64", "versions": "1+", "default": "-1", "ignorable": false,
          "about": "The timestamp associated with the returned offset." },
        { "name": "Offset", "type": "int64", "versions": "1+", "default": "-1", "ignorable": false,
          "about": "The returned offset." },
        { "name": "LeaderEpoch", "type": "int32", "versions": "4+", "default": "-1" }
      ]}
    ]}
  ]
}


## Uuid protocol class (verbatim)
package ssg.legoflow.messaging.kafka.protocol;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Raw 16-byte UUID conversions for the Kafka wire format.
 *
 * <p>Kafka's {@code uuid} field type is the 16-byte binary form of a
 * {@link java.util.UUID}: the most-significant 64 bits followed by the
 * least-significant 64 bits, both big-endian. These conversions are
 * used by the protocol models (topic IDs from v13+) and by tests; the
 * codec itself only ever handles the raw {@code byte[]} form
 * (see {@code KafkaCodecPrimitives#readUuid}/{@code #writeUuid}).
 *
 * <p>Public because codec classes in other packages validate uuid
 * field lengths via {@link #isValid(byte[])} and tests read/write the
 * wire form.
 */
public final class Uuid {

    /** The length in bytes of every Kafka {@code uuid} field. */
    public static final int SIZE = 16;

    private Uuid() {
    }

    /**
     * Encodes {@code uuid} in its 16-byte wire form.
     *
     * @param uuid the UUID to encode, not {@code null}
     * @return a fresh 16-byte array, most-significant bits first
     */
    public static byte[] bytes(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid must not be null");
        }
        byte[] out = new byte[SIZE];
        ByteBuffer buf = ByteBuffer.wrap(out);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return out;
    }

    /**
     * Decodes the 16-byte wire form of a UUID.
     *
     * @param bytes exactly {@link #SIZE} bytes in wire form, not {@code null}
     * @return the decoded UUID
     * @throws IllegalArgumentException if the length is not 16
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != SIZE) {
            throw new IllegalArgumentException(
                    "uuid bytes must be " + SIZE + " bytes, got "
                            + (bytes == null ? "null" : bytes.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    /**
     * Returns {@code true} when {@code bytes} is a structurally valid
     * Kafka uuid field ({@code null}-checked, 16 bytes).
     *
     * @param bytes the bytes to check
     * @return true when the length is exactly 16
     */
    public static boolean isValid(byte[] bytes) {
        return bytes != null && bytes.length == SIZE;
    }
}

