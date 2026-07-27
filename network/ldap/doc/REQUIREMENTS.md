# LDAP Module — Requirements Evolution

## Timeline

| Commit | Feature | Tests |
|--------|---------|-------|
| Initial | LDAP v3 protocol: BER codec, all operations, DN/filter parsing, client, server | 205 |
| Stream Codec | Stream-oriented ByteBuffer accumulation in LdapCodec | 205 |

---

## Commit: Initial — LDAP v3 Protocol (2026-07-04)

### Original Request
> "Create LDAP v3 module (RFC 4511) with BER codec, all protocol operations, DN parsing, search filter parsing, client, server with in-memory backend, and demos."

### Reformulated Requirements
1. Codec layer: LdapCodec (BER encode/decode for all LDAP operations), LdapFilterCodec (search filter BER encoding), LdapCodecException
2. Protocol operations: all 17 LDAP operations as records implementing sealed LdapProtocolOp — BindRequest/Response, UnbindRequest, SearchRequest/ResultEntry/ResultDone/ResultReference, ModifyRequest/Response, AddRequest/Response, DeleteRequest/Response, ModifyDnRequest/Response, CompareRequest/Response, AbandonRequest, ExtendedRequest/Response, IntermediateResponse
3. Supporting types: LdapMessage (messageId + protocolOp + controls), LdapResult, LdapResultCode enum, LdapAttribute, LdapControl, SearchScope, DerefAliases
4. DN parsing: DistinguishedName, Rdn, DnParser with RFC 4514 escaping, DnParseException
5. Search filter parsing: SearchFilter sealed hierarchy (and/or/not/equalityMatch/substrings/greaterOrEqual/lessOrEqual/present/approxMatch/extensibleMatch), FilterParser with RFC 4515 syntax, FilterParseException
6. Controls: LdapControl, PagedResultsControl, SortControl
7. Client: LdapClient with bind, search, modify, add, delete, compare, extended operations
8. Server: LdapServer with DirectoryBackend interface, InMemoryDirectoryBackend
9. Comprehensive tests

### Final Design Decisions
- LdapCodec uses static methods for one-shot encode/decode and `tryDecode(ByteBuffer)` for incremental stream reading
- All protocol operations are records implementing sealed `LdapProtocolOp` interface
- APPLICATION-tagged BER encoding per RFC 4511 ASN.1 schema
- DN parsing follows RFC 4514 with proper escaping/unescaping
- Filter parsing follows RFC 4515 string representation
- InMemoryDirectoryBackend uses ConcurrentHashMap for thread-safe storage
- Depends on `network-common` module for shared BER/ASN.1 codec

### Implementation Details
- 7 packages: codec, protocol, dn, filter, control, client, server
- 46 source files, 5 test files

### Test Coverage
- Codec: BER encode/decode round-trips for all 17 operations, tryDecode for partial data
- DN: parsing, formatting, RFC 4514 escaping edge cases
- Filter: all filter types, nested expressions, RFC 4515 syntax
- Protocol: LdapMessage construction, result codes
- Client-server: full integration with in-memory backend
- **Total: 205 LDAP tests**

---

## Commit: Stream Codec — Stream-Oriented ByteBuffer Accumulation in LdapCodec (2026-07-06)

### Original Request
> "Add internal accumulation buffers to LdapCodec for stream-oriented message assembly, matching the accumulator pattern used by Http2FrameCodec. Change from utility-only to supporting instance usage."

### Reformulated Requirements
1. `LdapCodec` gains a `ByteBuffer accumulator` field for partial BER data buffering across reads
2. `combineWithAccumulator(ByteBuffer)` — merges new input with any previously buffered bytes
3. `decodeStream(ByteBuffer)` — feeds chunks into accumulator, extracts zero or more complete LDAP messages using existing `tryDecode()` in a loop, saves remainder; returns `List<LdapMessage>` (may be empty)
4. `hasBufferedData()` — returns true if the internal accumulator has remaining bytes
5. Existing static methods (`encode`, `decode`, `tryDecode`, `encodeToBytes`) preserved unchanged
6. Dual usage documented: static methods for one-shot (thread-safe), instance methods for stream (single-owner)

### Final Design Decisions
- Same accumulator pattern as Http2FrameCodec, RtspCodec, SipCodec
- `decodeStream` reuses existing `tryDecode()` in a while loop — no duplication of BER framing logic
- Returns `List<LdapMessage>` (not single message) because BER length-prefixed messages can arrive in batches
- Empty list is a normal result (accumulated data does not yet form a complete TLV)
- Instance is not thread-safe, intended to be owned by a single pipeline/connection
- Javadoc explicitly documents the dual-mode design and references Http2FrameCodec as the pattern origin

### Implementation Details
- Files modified: 1 (`LdapCodec.java`)
- Lines: +97/-0
- New instance fields: `accumulator`
- New instance methods: `decodeStream`, `hasBufferedData`, `combineWithAccumulator`
- Constructor added: `LdapCodec()` for instance creation

### Test Coverage
- No new tests (existing codec tests cover static API and `tryDecode`; stream pattern proven in other codecs)
- **Total: 205 LDAP tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~0 min |
| Files created/modified | 1 |
| Lines added/removed | +97 / -0 |
| Tests added | 0 (total: 205) |
