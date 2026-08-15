# Cluster Discovery Module — DNS-SD / mDNS

## Commit: Phase 2 — DNS-SD / mDNS Service Discovery (2026-08-15)

### Original Request
> Implement cluster protocols for the lego-flow project. Choose the most popular protocols for each cluster functionality (sharing state, workload balancing, discovery, optimized processing, etc.) covering generic networking as well as HTTP-related activities. Create new protocols under the `network/cluster` module with smart module grouping.

### Reformulated Requirements

1. **DNS-SD Discovery Protocol** — Implement DNS-based Service Discovery per RFC 6762 (mDNS) and RFC 8305 (DNS-SD) for zero-config local network clustering
2. **mDNS Packet Codec** — Encode/decode DNS wire format (RFC 1035) with multicast-specific flags
3. **mDNS Responder** — Announce local services, respond to queries, send goodbye on leave
4. **mDNS Querier** — Send multicast queries, process responses, cache records with TTL
5. **Conflict Resolution** — Per RFC 6762 §8: probe before announce, rename on conflict
6. **Service Browser** — Browse all instances of a service type on the local network
7. **Cluster Membership** — Implement `ClusterMembership` SPI via DNS-SD discovery
8. **Multi-Interface Support** — Handle multiple network interfaces with per-interface records
9. **TXT Record Metadata** — Arbitrary key-value metadata in service announcements

### Final Design Decisions

- **DNS Codec**: Reuse `network/dns` module's DNS encoder/decoder (RFC 1035 wire format)
- **Multicast**: `224.0.0.251:5353` (IPv4) for mDNS; virtual threads for listener loops
- **Record Chain**: PTR → SRV → A + TXT per RFC 8305 three-record chain
- **Cache**: TTL-based record caching in querier; half-TTL refresh strategy
- **Conflict Resolution**: Up to 3 probes with 250ms intervals; random suffix rename on conflict
- **Interface Detection**: `MdnsInterfaceManager` enumerates network interfaces, filters loopback/appropriate ones
- **Cluster Node mapping**: Each resolved service record becomes a `ClusterNode` via `DnsSdDiscovery`

### Implementation Details

**Source Files Created (9):**
- `DnsSdDiscovery.java` — ClusterMembership implementation via DNS-SD
- `DnsSdConfig.java` — Configuration record (service type, instance name, port, TXT, TTL)
- `DnsSdServiceRecord.java` — Service record with PTR/SRV/A/TXT record builders
- `DnsSdRecordBuilder.java` — DNS record construction and response building
- `MdnsResponder.java` — Multicast DNS responder (announces, responds, goodbye)
- `MdnsQuerier.java` — Multicast DNS querier (queries, resolves, caches)
- `MdnsPacketCodec.java` — mDNS-specific packet encoding/decoding
- `MdnsConflictResolver.java` — Name conflict detection and resolution
- `MdnsInterfaceManager.java` — Network interface enumeration and filtering
- `DnsSdBrowser.java` — Service browser with event callbacks

**Test Files Created (9):**
- `DnsSdConfigTest.java` — Configuration validation (13 tests)
- `DnsSdServiceRecordTest.java` — Record construction, equality (14 tests)
- `DnsSdRecordBuilderTest.java` — DNS record building, TXT escaping (11 tests)
- `MdnsPacketCodecTest.java` — Packet encode/decode, flags (16 tests)
- `MdnsResponderTest.java` — Response sending, announcements (6 tests)
- `MdnsQuerierTest.java` — Query sending, resolution (8 tests, 1 skipped)
- `DnsSdBrowserTest.java` — Service browsing, lifecycle (9 tests, 1 skipped)
- `DnsSdConflictResolutionTest.java` — Probe, rename, re-probe (12 tests)
- `DnsSdDiscoveryIntegrationTest.java` — Multi-node cluster simulation (6 tests, 3 skipped)

### Test Coverage

| Test Class | Tests | Pass | Skipped | Coverage |
|---|---|---|---|---|
| DnsSdConfigTest | 13 | 13 | 0 | Validation, defaults, builders |
| DnsSdServiceRecordTest | 14 | 14 | 0 | Record chain, equality, toString |
| DnsSdRecordBuilderTest | 11 | 11 | 0 | PTR/SRV/A/TXT building, TXT escape |
| MdnsPacketCodecTest | 16 | 16 | 0 | Encode/decode, flags, goodbyes |
| MdnsResponderTest | 6 | 6 | 0 | Announce, respond, goodbye |
| MdnsQuerierTest | 8 | 7 | 1 | Query, resolve, cache |
| DnsSdBrowserTest | 9 | 8 | 1 | Browse, events, lifecycle |
| DnsSdConflictResolutionTest | 12 | 12 | 0 | Probe, conflict, rename |
| DnsSdDiscoveryIntegrationTest | 6 | 3 | 3 | Single/multi-node discovery |
| **Total** | **95** | **90** | **5** | |

> Note: 5 tests skipped require multicast loopback support (network-dependent).

### Cost Estimate

| Metric | Value |
|--------|-------|
| Files created | 20 (9 src + 9 test + 2 doc) |
| Lines added | ~2,800 |
| Tests added | 95 (90 passing, 5 skipped) |
| Modules modified | demos/pom.xml |

---
