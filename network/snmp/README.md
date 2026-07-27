
# Lego Flow SNMP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-113-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

SNMPv3 protocol module for the Lego Flow framework, providing agent (server) and manager (client) implementations with USM security and VACM access control.

## Overview

This module implements SNMP version 3 (RFC 3411-3418), enabling Java applications to build SNMP agents for network device management and SNMP managers for monitoring and configuration. The architecture layers protocol handling on top of the framework's shared BER/ASN.1 codec:

```
SNMP Manager / Agent (application layer)
  -> USM Security Engine (authentication, privacy, key management)
    -> VACM Access Control (view-based OID access policies)
      -> MIB Tree (sorted in-memory OID-value store)
        -> BER Codec (SNMPv3 message encode/decode)
          -> UDP Transport (DatagramSocket)
```

## Features

- **SNMPv3** -- full v3 message format with scoped PDUs, message IDs, and security model
- **7 PDU types** -- GetRequest, GetNextRequest, Response, SetRequest, GetBulkRequest, InformRequest, TrapV2
- **12 SMIv2 data types** -- Integer32, Counter32, Counter64, Gauge32, TimeTicks, OctetString, OID, IpAddress, Opaque, Null, plus exception values (NoSuchObject, NoSuchInstance, EndOfMibView)
- **USM security** -- User-based Security Model with HMAC-MD5-96 and HMAC-SHA-96 authentication, DES-CBC and AES-128-CFB privacy
- **VACM access control** -- security-to-group mapping, access table, view tree family with OID mask filtering
- **Agent (server)** -- UDP listener, MIB tree request processing, trap and inform notification sender
- **Manager (client)** -- GET, GETNEXT, GETBULK, SET operations with retransmission and timeout, trap/inform listener
- **Key derivation** -- RFC 3414 password-to-key and key localization algorithms
- **MIB tree** -- thread-safe sorted OID store with exact lookup, lexicographic next, subtree queries

## Quick Start

### Set up a MIB tree and start an agent

```java
var mibTree = new MibTree();
mibTree.put("1.3.6.1.2.1.1.1.0", SnmpValue.OctetString.of("My SNMP Agent"));
mibTree.put("1.3.6.1.2.1.1.3.0", new SnmpValue.TimeTicks(5000));
mibTree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("agent-host"));

var engineId = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
var usmEngine = new UsmEngine(engineId);
var agent = new SnmpAgent(161, mibTree, usmEngine);
agent.start();
```

### Query the agent with a manager

```java
var managerEngine = new UsmEngine(new byte[]{0x0A, 0x0B, 0x0C});
var manager = new SnmpManager("127.0.0.1", 161, managerEngine);

// GET request
SnmpPdu.Response response = manager.get(
    ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
String sysDescr = ((SnmpValue.OctetString) response.varBindList()
    .get(0).value()).asString();

// GETNEXT request (walk)
SnmpPdu.Response next = manager.getNext(
    ObjectIdentifier.parse("1.3.6.1.2.1.1"));

// GETBULK request (efficient table retrieval)
SnmpPdu.Response bulk = manager.getBulk(0, 10,
    ObjectIdentifier.parse("1.3.6.1.2.1.1"));

// SET request
manager.set(new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"),
    SnmpValue.OctetString.of("new-hostname")));
```

### USM authentication and privacy

```java
var engineId = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};

// Derive localized keys from passwords
byte[] authKey = UsmKeyUtils.deriveLocalizedKey(
    "authPassword", engineId, AuthProtocol.HMAC_SHA_96);
byte[] privKey = UsmKeyUtils.derivePrivLocalizedKey(
    "privPassword", engineId, AuthProtocol.HMAC_SHA_96, PrivProtocol.AES_128_CFB);

// Create user with authPriv security level
UsmUser user = UsmUser.authPriv("admin",
    AuthProtocol.HMAC_SHA_96, authKey,
    PrivProtocol.AES_128_CFB, privKey);

usmEngine.addUser(user);
manager.setUser(user, SecurityLevel.AUTH_PRIV);
```

### Send traps and informs

```java
agent.sendTrapV2("192.168.1.100", 162, 5000,
    SnmpOids.COLD_START,
    new VarBind(SnmpOids.SYS_NAME, SnmpValue.OctetString.of("agent-1")));

agent.sendInform("192.168.1.100", 162, 5000,
    SnmpOids.LINK_DOWN);
```

### VACM access control

```java
var vacm = new VacmAccessControl();
vacm.addSecurityToGroup(3, "admin", "adminGroup");
vacm.addAccess("adminGroup", "", 3, SecurityLevel.AUTH_PRIV,
    "fullView", "fullView", "fullView");
vacm.addView("fullView", ObjectIdentifier.parse("1.3.6.1"), null, true);

boolean allowed = vacm.isAccessAllowed(3, "admin", SecurityLevel.AUTH_PRIV,
    "", VacmAccessControl.AccessType.READ,
    ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
```

## Package Structure

```
ssg.legoflow.network.snmp/
+-- protocol/          -- Message format: SnmpMessage, ScopedPdu, SnmpPdu (7 types),
|                         VarBind/VarBindList, SnmpValue (12 types), SnmpCodec (BER),
|                         SecurityLevel, UsmSecurityParameters, SnmpOids
+-- security/          -- USM: UsmEngine (auth + priv), UsmUser, UsmKeyUtils,
|                         AuthProtocol, PrivProtocol, VacmAccessControl (RFC 3415)
+-- server/            -- Agent: SnmpAgent (UDP, request processing, trap/inform sender),
|                         MibTree (sorted OID-value store)
+-- client/            -- Manager: SnmpManager (GET/GETNEXT/GETBULK/SET, trap listener)
```

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- lifecycle management, virtual threads
- `lego-flow-network-common` -- shared BER/ASN.1 codec, ObjectIdentifier

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
