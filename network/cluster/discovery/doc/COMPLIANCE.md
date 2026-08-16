# DNS-SD/mDNS — Compliance Report

## Specifications Covered
- RFC 6762 — Multicast DNS (mDNS)
- RFC 8305 — DNS-Based Service Discovery (DNS-SD)
- RFC 1035 — DNS wire format (packet encoding)
- RFC 2782 — SRV resource records

## Compliance Matrix

### RFC 6762 — Multicast DNS

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §2.1 | Multicast address 224.0.0.251 | ✅ Implemented | `MulticastSocket` bound to 224.0.0.251; `DnsSdDiscoveryTest` |
| §2.2 | UDP port 5353 | ✅ Implemented | `DatagramSocket` on port 5353; `MdnsResponderTest` |
| §3 | Unicast responses to unicast queries | ✅ Implemented | Query source check in responder; `MdnsResponderTest` |
| §4 | Cache-flushing bit (top bit of TTL) | ✅ Implemented | TTL encoding/decoding in `MdnsPacketCodec`; `MdnsPacketCodecTest` |
| §5 | Name conflict resolution (ASCII step-down) | ✅ Implemented | `MdnsConflictResolver` with `-local`, `-local-2`, etc.; `MdnsConflictResolverTest` |
| §7 | Unicast DNS query responses | ✅ Implemented | Query response in `MdnsResponder`; `MdnsResponderTest` |
| §8 | Probing and announcement (3 probes + announcement) | ✅ Implemented | `MdnsResponder` probe sequence with configurable count; `DnsSdDiscoveryTest` |
| §10 | Goodbye packets | ✅ Implemented | `MdnsResponder` sends TTL=0 on leave; `DnsSdDiscoveryTest` |
| §11 | Duplicate packet suppression | ✅ Implemented | `DnsSdBrowser` deduplicates by record identity; `DnsSdBrowserTest` |
| §13 | Record lifetime management | ✅ Implemented | TTL expiry in `DnsSdBrowser` cache; `DnsSdBrowserTest` |

### RFC 8305 — DNS-Based Service Discovery

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §4 | Service instance naming (`Instance.type.domain`) | ✅ Implemented | `DnsSdConfig.instanceFqdn()`; `DnsSdConfigTest` |
| §4.1 | Service type registration (`_Service._Proto`) | ✅ Implemented | `_legoflow._tcp` pattern; `DnsSdConfigTest` |
| §5 | PTR, SRV, TXT record structure | ✅ Implemented | `DnsSdServiceRecord` with all fields; `DnsSdRecordBuilder` |
| §5.2 | SRV record (priority, weight, port, target) | ✅ Implemented | `DnsSdRecordBuilder` generates SRV records; `DnsSdRecordBuilderTest` |
| §5.3 | TXT record (key-value metadata) | ✅ Implemented | `DnsSdConfig.txtAttributes()`; TXT encoding in `MdnsPacketCodec` |
| §8 | Browse → Resolve flow | ✅ Implemented | `DnsSdBrowser` queries PTR, resolves to SRV+TXT; `DnsSdBrowserTest` |

### RFC 1035 — DNS Wire Format

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §4.1.1 | Message header (ID, flags, counts) | ✅ Implemented | `MdnsPacketCodec` header encoding; `MdnsPacketCodecTest` |
| §4.1.2 | Resource record format | ✅ Implemented | RR encoding (name, type, class, TTL, data); `MdnsPacketCodecTest` |
| §4.1.4 | Domain name compression (pointer) | ✅ Implemented | Pointer encoding (`\xC0\x0C`); `MdnsPacketCodecTest` |
| §4.3.1 | FQDN encoding (label length prefixes) | ✅ Implemented | Label-by-label encoding; `MdnsPacketCodecTest` |
