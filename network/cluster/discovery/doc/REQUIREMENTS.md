# Cluster Discovery — DNS-SD/mDNS — Requirements & Design

## Commit: (planned) — DNS-SD/mDNS Discovery

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality"

### Reformulated Requirements
1. Implement RFC 6762 (mDNS) multicast DNS communication on link-local 224.0.0.251:5353
2. Implement RFC 8305 (DNS-SD) three-record chain: PTR → SRV → A/AAAA
3. Service probing phase: detect name conflicts before announcing (RFC 6762 §8)
4. Multicast announcement on join, goodbye on leave
5. Query processing: resolve full service chain
6. Cache management: TTL-based record expiry
7. Multiple interface support: per-interface DNS-SD records
8. Integrate with `network/dns` module codec (reuse DNS packet encoder/decoder)
9. Implement `ClusterMembership` SPI via DNS-SD
10. Service browsing: enumerate all instances of a service type

### Final Design Decisions
- **Package:** `ssg.legoflow.network.cluster.dns`
- **Module:** `network/cluster/discovery` (under `network/cluster` aggregator)
- **Dependencies:** cluster-core, dns, service, network-common
- **Reuse DNS codec** from `network/dns` for RFC 1035 wire format
- **Multicast socket** bound to 224.0.0.251 (IPv4) and [FF02::FB] (IPv6)
- **Probing:** up to 3 probes with random delay; conflict → rename + reprobe

### Implementation Details
- `DnsSdDiscovery` — ClusterMembership implementation
- `DnsSdConfig` — service name, instance name, port, TXT metadata, interface
- `DnsSdServiceRecord` — PTR, SRV, TXT, A record set
- `DnsSdRecordBuilder` — builds valid DNS-SD records from config
- `MdnsResponder` — responds to multicast queries; sends announcements
- `MdnsQuerier` — sends queries; processes responses; manages cache
- `MdnsPacketCodec` — encode/decode multicast DNS packets
- `MdnsConflictResolver` — duplicate name detection and resolution
- `MdnsInterfaceManager` — per-interface record management
- `DnsSdBrowser` — enumerate service instances

### Test Coverage
- `DnsSdRecordBuilderTest` — valid record construction
- `DnsSdServiceRecordTest` — PTR→SRV→A chain
- `MdnsPacketCodecTest` — RFC 1035 wire format compliance
- `MdnsResponderTest` — query response; announcement on start
- `MdnsQuerierTest` — chain resolution; TTL cache
- `DnsSdDiscoveryIntegrationTest` — 3-node loopback cluster
- `DnsSdConflictResolutionTest` — duplicate detection + rename
- `DnsSdMultiInterfaceTest` — multiple NIC support
- `DnsSdBrowserTest` — service enumeration

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~12000 |
| Agent tool calls | ~20 |
| Agent wall time | ~25 min |
| Files created/modified | ~25 |
| Lines added/removed | +1500 / -0 |
| Tests added | ~9 (total: ~9) |
