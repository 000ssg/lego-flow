# SNMP Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 113
- **Dependencies**: blocks (DP/DF), service (lifecycle), network-common (BER/ASN.1 codec)
- **Standards**: SNMPv3 (RFC 3411-3418)

---

## Requirements

### BER Codec (SnmpCodec)
1. Encode and decode SNMPv3 message wrapper: version, header data (msgID, msgMaxSize, msgFlags, msgSecurityModel), security parameters, scoped PDU
2. Encode and decode all 7 PDU types as context-specific implicit constructed BER tags
3. Encode and decode VarBindList as SEQUENCE of SEQUENCE(OID, value) pairs
4. Encode and decode all 12 SMIv2 value types: Integer32, Counter32, Counter64, Gauge32, TimeTicks, OctetString, OID, IpAddress, Opaque, Null, NoSuchObject, NoSuchInstance, EndOfMibView
5. Encode and decode USM security parameters: engineID, engineBoots, engineTime, userName, authParams, privParams
6. Application-tagged values (Counter32, Gauge32, TimeTicks, IpAddress, Opaque, Counter64) encoded as context-specific primitive tags with tag numbers 0-6
7. Exception values (NoSuchObject, NoSuchInstance, EndOfMibView) distinguished by empty payload at context-specific tags 0-2
8. Validate packet structure and reject malformed data with SnmpCodecException
9. Stateless codec -- all static methods, thread-safe

### SMIv2 Data Types (SnmpValue)
1. Integer32: 32-bit signed integer
2. Counter32: 32-bit unsigned counter (0 to 2^32-1), wraps
3. Counter64: 64-bit unsigned counter
4. Gauge32: 32-bit unsigned gauge (0 to 2^32-1), latches
5. TimeTicks: hundredths of a second since epoch (unsigned 32-bit)
6. OctetString: arbitrary byte array with UTF-8 convenience methods
7. Oid: ASN.1 OBJECT IDENTIFIER value
8. IpAddress: exactly 4 bytes (IPv4)
9. Opaque: arbitrary ASN.1 encoded data
10. Null: for GET request variable bindings
11. NoSuchObject, NoSuchInstance, EndOfMibView: exception values in responses
12. Sealed interface hierarchy for exhaustive pattern matching
13. Defensive copies for all byte array fields
14. Validation on construction (range checks for Counter32, Gauge32, TimeTicks; length check for IpAddress)

### Well-Known OIDs (SnmpOids)
1. MIB-2 root hierarchy: INTERNET, MGMT, MIB_2, ENTERPRISES
2. System group: SYS_DESCR, SYS_OBJECT_ID, SYS_UP_TIME, SYS_CONTACT, SYS_NAME, SYS_LOCATION, SYS_SERVICES
3. Interface group: INTERFACES, IF_NUMBER
4. Trap OIDs: SNMP_TRAP_OID, COLD_START, WARM_START, LINK_DOWN, LINK_UP, AUTH_FAILURE
5. USM statistics: USM_STATS and 6 counter OIDs

### Security Level (SecurityLevel)
1. NO_AUTH_NO_PRIV (0x00): no authentication, no privacy
2. AUTH_NO_PRIV (0x01): authentication only
3. AUTH_PRIV (0x03): authentication and privacy
4. Reject invalid combination 0x02 (privacy without authentication)
5. Derive from and convert to msgFlags bits 0-1

### USM Security Engine (UsmEngine)
1. Engine ID management with defensive copying
2. Engine boots counter and engine time tracking
3. User table with concurrent add/get/remove operations
4. HMAC-MD5-96 authentication: compute and verify 12-byte truncated HMAC-MD5 digest
5. HMAC-SHA-96 authentication: compute and verify 12-byte truncated HMAC-SHA-1 digest
6. DES-CBC encryption: 8-byte DES key from first half of privKey, pre-IV from second half, IV = pre-IV XOR salt, pad to 8-byte boundary
7. AES-128-CFB encryption: 16-byte AES key, IV = boots(4) + time(4) + salt(8), no padding
8. Monotonic salt counter for encryption IV uniqueness
9. Thread-safe via ConcurrentHashMap and AtomicInteger

### USM Key Derivation (UsmKeyUtils)
1. Password-to-key (RFC 3414 A.2.1): cyclic expansion of password to 1 MB, then hash
2. Key localization (RFC 3414 A.2.2): Hash(masterKey + engineID + masterKey)
3. Combined deriveLocalizedKey convenience method
4. Privacy key derivation with truncation to cipher key length
5. Stateless, thread-safe

### VACM Access Control (VacmAccessControl)
1. Security-to-group table: map (securityModel, securityName) to groupName
2. Access table: map (groupName, contextPrefix, securityModel, securityLevel) to read/write/notify view names
3. View tree family table: OID subtree entries with inclusion/exclusion and bit masks
4. isInView: check OID against named view with subtree mask matching
5. isAccessAllowed: end-to-end check combining all three tables
6. Context prefix matching with empty-prefix fallback
7. Thread-safe via ConcurrentHashMap and CopyOnWriteArrayList

### MIB Tree (MibTree)
1. Sorted in-memory OID-value store using ConcurrentSkipListMap
2. Exact get by OID
3. Lexicographic getNext (higherEntry) for GETNEXT operations
4. getCeiling (ceilingEntry) for inclusive lookups
5. Subtree queries via tailMap with prefix match
6. Put (create/update), remove, contains, size, isEmpty, clear
7. Unmodifiable entries() view
8. Lock-free concurrent access

### Agent (SnmpAgent)
1. UDP DatagramSocket listener on configurable port (default 161)
2. Virtual thread accept loop and per-request handler threads
3. Process GetRequest: lookup each OID in MibTree, return NoSuchObject for missing
4. Process GetNextRequest: lexicographic next for each OID, return EndOfMibView at end
5. Process GetBulkRequest: non-repeaters (like GETNEXT) then repeater loop up to maxRepetitions
6. Process SetRequest: two-phase validate-then-apply, reject Null values as notWritable
7. Process InformRequest: acknowledge with matching Response
8. Send TrapV2: build trap PDU with sysUpTime.0 and snmpTrapOID.0, send via UDP
9. Send InformRequest: build inform PDU with reportable flag
10. AutoCloseable with socket cleanup

### Manager (SnmpManager)
1. UDP DatagramSocket client targeting configurable host:port
2. GET, GETNEXT, GETBULK, SET, INFORM operations
3. Retransmission with configurable timeout (default 5000 ms) and retries (default 2)
4. Atomic request ID and message ID counters
5. USM user and security level configuration
6. Remote engine ID/boots/time tracking (for engine discovery)
7. Trap/inform listener on virtual thread with Consumer callback
8. AutoCloseable with socket and listener cleanup

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
