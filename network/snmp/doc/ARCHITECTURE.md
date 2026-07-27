# SNMP Module -- Architecture

This document describes the architectural decisions for the SNMP module.

---

## Protocol Overview

SNMP (Simple Network Management Protocol) is a UDP-based protocol for monitoring and managing network devices. The Lego Flow implementation targets SNMPv3 (RFC 3411-3418), which adds the User-based Security Model (USM) and View-based Access Control Model (VACM) over the earlier SNMPv1/v2c protocol operations.

## Layered Architecture

```mermaid
graph TD
    L1["Manager / Agent<br/>(UDP transport, request dispatch, trap/inform)"]
    L2["VACM Access Control<br/>(security-to-group, access table, view tree family)"]
    L3["USM Security Engine<br/>(HMAC-MD5-96/SHA-96 auth, DES-CBC/AES-128-CFB priv,<br/>key derivation, replay protection)"]
    L4["MIB Tree<br/>(ConcurrentSkipListMap, sorted OID storage,<br/>exact/next/ceiling/subtree queries)"]
    L5["BER Codec<br/>(SNMPv3 message, ScopedPdu, 7 PDU types,<br/>12 SMIv2 value types, USM security params)"]
    L6["network-common module<br/>(BerEncoder, BerDecoder, ASN.1 types, ObjectIdentifier)"]
    L7["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
```

## PDU Types

All 7 SNMPv3 PDU types, encoded as context-specific implicit constructed BER tags:

| PDU Type | Tag | Direction | Purpose |
|----------|-----|-----------|---------|
| GetRequest | [0] | Manager->Agent | Retrieve values for specific OIDs |
| GetNextRequest | [1] | Manager->Agent | Retrieve next OIDs in lexicographic order |
| Response | [2] | Agent->Manager | Response to any request PDU |
| SetRequest | [3] | Manager->Agent | Set values for specific OIDs |
| GetBulkRequest | [5] | Manager->Agent | Efficient bulk retrieval (non-repeaters + repetitions) |
| InformRequest | [6] | Manager->Manager | Acknowledged notification |
| TrapV2 | [7] | Agent->Manager | Unacknowledged notification |

## SNMPv3 Message Structure

```mermaid
graph TD
    MSG["SNMPv3 Message"] --> HDR["Header Data<br/>(msgID, msgMaxSize, msgFlags, msgSecurityModel)"]
    MSG --> SEC["Security Parameters<br/>(USM: engineID, boots, time, user, authParams, privParams)"]
    MSG --> SPD["Scoped PDU<br/>(contextEngineID, contextName, PDU)"]
    SPD --> PDU["PDU<br/>(requestID, error-status, error-index, VarBindList)"]
    PDU --> VBL["VarBindList"]
    VBL --> VB1["VarBind<br/>(OID, Value)"]
    VBL --> VB2["VarBind<br/>(OID, Value)"]
    VBL --> VBN["..."]
```

## Sealed Type Hierarchies

The module uses two key sealed type hierarchies for exhaustive pattern matching:

```mermaid
graph TD
    subgraph "SnmpPdu (sealed interface)"
        GR["GetRequest"]
        GNR["GetNextRequest"]
        RSP["Response"]
        SR["SetRequest"]
        GBR["GetBulkRequest"]
        IR["InformRequest"]
        T2["TrapV2"]
    end

    subgraph "SnmpValue (sealed interface)"
        I32["Integer32"]
        C32["Counter32"]
        C64["Counter64"]
        G32["Gauge32"]
        TT["TimeTicks"]
        OS["OctetString"]
        OID["Oid"]
        IP["IpAddress"]
        OP["Opaque"]
        NUL["Null"]
        NSO["NoSuchObject"]
        NSI["NoSuchInstance"]
        EOM["EndOfMibView"]
    end
```

## USM Security Architecture

```mermaid
graph TD
    PWD["User Password"] --> P2K["passwordToKey<br/>(cyclic 1MB hash, RFC 3414 A.2.1)"]
    P2K --> MK["Master Key"]
    MK --> LOC["localizeKey<br/>(Hash(key+engineID+key), RFC 3414 A.2.2)"]
    LOC --> AK["Auth Key<br/>(16 bytes MD5 / 20 bytes SHA-1)"]
    LOC --> PK["Priv Key<br/>(16 bytes, truncated)"]

    AK --> AUTH["Authentication<br/>(HMAC-MD5-96 or HMAC-SHA-96)"]
    AUTH --> HMAC["12-byte truncated HMAC<br/>(placed in msgAuthenticationParameters)"]

    PK --> PRIV["Encryption<br/>(DES-CBC or AES-128-CFB)"]
    PRIV --> ENC["Encrypted ScopedPDU +<br/>privParams salt/IV"]
```

### Authentication Flow

1. Sender zeroes the 12-byte authParams field in the encoded message
2. Computes HMAC over entire message using localized auth key
3. Truncates HMAC to 12 bytes and places it in authParams
4. Receiver repeats the process and compares digests

### Encryption Flow

- **DES-CBC (RFC 3414)**: DES key = first 8 bytes of privKey, pre-IV = last 8 bytes, IV = pre-IV XOR salt, data padded to 8-byte boundary
- **AES-128-CFB (RFC 3826)**: AES key = first 16 bytes of privKey, IV = boots(4) + time(4) + salt(8), no padding needed (CFB mode)

## VACM Access Control Architecture

```mermaid
graph TD
    REQ["Access Check Request<br/>(securityModel, securityName,<br/>securityLevel, contextName,<br/>accessType, OID)"] --> S2G["Security-to-Group Table<br/>(securityModel + securityName -> groupName)"]
    S2G --> AT["Access Table<br/>(groupName + context + secModel + secLevel<br/>-> readView / writeView / notifyView)"]
    AT --> VTF["View Tree Family Table<br/>(viewName -> list of subtree entries<br/>with inclusion/exclusion and OID masks)"]
    VTF --> DEC{{"OID in view?"}}
    DEC -->|"Yes"| ALLOW["Access Allowed"]
    DEC -->|"No"| DENY["Access Denied"]
```

Three tables from RFC 3415:
- **vacmSecurityToGroupTable**: maps (securityModel, securityName) to a group name
- **vacmAccessTable**: maps (groupName, contextPrefix, securityModel, securityLevel) to view names for read/write/notify
- **vacmViewTreeFamilyTable**: defines OID subtrees with inclusion/exclusion and bit masks for each view

## Agent Architecture

```mermaid
graph TD
    UDP["UDP Socket<br/>(DatagramSocket)"] --> RECV["Accept Loop<br/>(virtual thread)"]
    RECV --> HANDLER["Request Handler<br/>(virtual thread per request)"]
    HANDLER --> DECODE["SnmpCodec.decodeMessage"]
    DECODE --> DISPATCH["processRequest<br/>(pattern match on PDU type)"]
    DISPATCH --> GET["processGet<br/>(MibTree.get per VarBind)"]
    DISPATCH --> GNXT["processGetNext<br/>(MibTree.getNext per VarBind)"]
    DISPATCH --> GBULK["processGetBulk<br/>(non-repeaters + repeater loop)"]
    DISPATCH --> SET["processSet<br/>(two-phase: validate then apply)"]
    DISPATCH --> INF["processInform<br/>(acknowledge with Response)"]
    GET --> ENCODE["SnmpCodec.encodeMessage"]
    GNXT --> ENCODE
    GBULK --> ENCODE
    SET --> ENCODE
    INF --> ENCODE
    ENCODE --> SEND["UDP send response"]
```

- Virtual threads for concurrent request handling (one per incoming datagram)
- SetRequest uses two-phase commit: validate all VarBinds, then apply
- GetBulkRequest processes non-repeaters (like GETNEXT) then repeaters in a loop up to maxRepetitions

## Manager Architecture

```mermaid
graph TD
    APP["Application"] --> OPS["get / getNext / getBulk / set / inform"]
    OPS --> BUILD["Build SnmpMessage<br/>(PDU, ScopedPdu, security params)"]
    BUILD --> SEND["UDP Send"]
    SEND --> RETRY{{"Timeout?"}}
    RETRY -->|"Yes, retries left"| SEND
    RETRY -->|"Yes, no retries"| FAIL["IOException"]
    RETRY -->|"No"| RECV["Receive Response"]
    RECV --> DECODE["SnmpCodec.decodeMessage"]
    DECODE --> RETURN["Return Response PDU"]

    TRAP["startTrapListener"] --> LISTEN["Virtual thread<br/>UDP receive loop"]
    LISTEN --> HANDLER["Consumer&lt;SnmpMessage&gt;<br/>callback"]
```

- Configurable timeout and retry count (default 5000 ms, 2 retries)
- Trap/inform listener runs on a separate virtual thread
- Request IDs and message IDs use atomic counters

## MIB Tree Design

The MibTree uses `ConcurrentSkipListMap<ObjectIdentifier, SnmpValue>` which provides:
- O(log n) exact lookup, insertion, removal
- O(log n) lexicographic next/ceiling via `higherEntry`/`ceilingEntry`
- Subtree queries via `tailMap` with prefix check
- Lock-free concurrent access (no explicit synchronization needed)

ObjectIdentifier implements `Comparable` using arc-by-arc comparison, so the skip list maintains natural OID ordering required by GETNEXT and GETBULK operations.

## BER Codec Design

SnmpCodec is stateless and thread-safe (all static methods). It delegates to the shared `network-common` BER library:
- Message structure: SEQUENCE(version, headerData, securityParams, scopedPdu)
- PDU types: context-specific implicit constructed tags [0]-[7]
- Application-tagged values (Counter32, Gauge32, etc.) are encoded as context-specific primitive tags since the shared library does not have an APPLICATION-class variant
- Exception values (NoSuchObject, NoSuchInstance, EndOfMibView) are distinguished from application values by empty payload

## Integration with Lego Flow

| Lego Flow Module | Usage in SNMP |
|------------------|---------------|
| `blocks` | DP<I,O> for data processing primitives, Statistics for metrics |
| `service` | Lifecycle management, virtual thread pools |
| `network-common` | BER encoder/decoder, ASN.1 types (Asn1Sequence, Asn1Integer, etc.), ObjectIdentifier with Comparable ordering |

## Thread Safety Model

- `SnmpCodec`: stateless, all static methods -- inherently thread-safe
- `UsmEngine`: ConcurrentHashMap for users, AtomicInteger for counters -- thread-safe
- `VacmAccessControl`: ConcurrentHashMap + CopyOnWriteArrayList -- thread-safe
- `MibTree`: ConcurrentSkipListMap -- lock-free concurrent access
- `SnmpAgent`: volatile running flag, virtual threads for request handling
- `SnmpManager`: volatile fields for user/security state, AtomicInteger counters

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
