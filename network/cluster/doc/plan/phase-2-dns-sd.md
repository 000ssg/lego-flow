# Phase 2 — DNS-SD / mDNS Discovery

## Module
`network/cluster/discovery`

## Goal
Zero-config local network service discovery. Nodes announce themselves via multicast DNS and discover peers without any external infrastructure (no etcd, no K8s, no Consul).

## Protocol Compliance

### RFC 6762 — Multicast DNS
- Link-local address: `224.0.0.251` (IPv4) / `[FF02::FB]` (IPv6), port `5353`
- Unicast response flag: responders may reply unicast for unicast queries
- Conflict resolution: probing phase before announcing (up to 3 probes)
- Cache timing: record TTL, half-TTL retransmission for final goodbyes
- Response suppression: if cached answer matches, suppress query

### RFC 8305 — DNS Service Discovery
- Three-record chain: PTR → SRV → A/AAAA
  - PTR: `_http._tcp.local.` → `MyService._http._tcp.local.`
  - SRV: `MyService._http._tcp.local.` → hostname, port, priority, weight
  - A/AAAA: hostname → IP address
- TXT record: arbitrary metadata as key-value pairs
- Query types: service enumeration ( PTR for `_http._tcp.local.` ) or specific instance lookup

## Design Decisions

### DNS Packet Codec
- Reuse `network/dns` module's BER/ASN.1-free DNS codec (RFC 1035 wire format)
- New `MdnsPacketCodec` wraps existing DNS encoder/decoder with multicast-specific behavior
- Multicast differences: no recursion desired bit, multicast-scope responses

### MdnsResponder
- UDP server socket bound to multicast address
- Responds to: queries for this node's SRV record, A record, PTR record
- Sends: initial announcement (on join), goodbye (on leave)
- Conflict detection: if another node responds during probing, pick new instance name

### MdnsQuerier
- Sends DNS queries to multicast address
- Processes responses: builds service record map (PTR→SRV→A chain)
- Caches records with TTL; refreshes at half-TTL
- Emits `ServiceResolved` and `ServiceRemoved` events

### DnsSdDiscovery — ClusterMembership Implementation
- On start: probe → announce → listen
- Membership derived from resolved service records for this cluster's service type
- Heartbeat: re-announce at TTL/2 interval
- Leave: send goodbye packet

## Testing Plan

### Codec Tests (RFC 1035 + RFC 6762 compliance)
- `MdnsPacketCodecTest`: encode/decode multicast DNS packets
  - Query with multicast flags set
  - Response with authoritative flag
  - SRV record: priority, weight, port, target
  - TXT record: key-value pairs
  - Multiple records per packet

### Record Tests (RFC 8305 compliance)
- `DnsSdServiceRecordTest`: PTR→SRV→A chain correctness
- `DnsSdRecordBuilderTest`: build valid records from config
  - Service name encoding: `_name._proto.domain`
  - Instance name: FQDN format
  - TXT: escape quotes and backslashes

### Responder/Querier Tests
- `MdnsResponderTest`: responds to queries within timeout; sends announcement on start
- `MdnsQuerierTest`: resolves service chain; TTL cache management
- `DnsSdMultiInterfaceTest`: handles multiple network interfaces; per-interface records

### Conflict Resolution (RFC 6762 §8)
- `DnsSdConflictResolutionTest`: detect duplicate during probe; rename and re-probe
- Verify no two nodes claim same instance name

### Integration Tests
- `DnsSdDiscoveryIntegrationTest`: 3-node local cluster using loopback multicast
  - Node A starts → resolves empty
  - Node B starts → A and B resolve each other
  - Node B stops → A detects B gone (TTL expiry)

## Demo Plan
`DnsSdDiscoveryDemo` — 3 nodes on loopback
1. Start node A → announces `_legoflow._tcp.local.`
2. Start node B → probes, announces; A discovers B
3. Start node C → all 3 discover each other
4. Browse services → list all instances with metadata
5. Stop node B → A and C detect B absence

## Files to Create
```
network/cluster/discovery/pom.xml
network/cluster/discovery/doc/REQUIREMENTS.md
src/main/java/.../cluster/dns/
  DnsSdDiscovery.java
  DnsSdConfig.java
  DnsSdServiceRecord.java
  DnsSdRecordBuilder.java
  MdnsResponder.java
  MdnsQuerier.java
  MdnsPacketCodec.java
  MdnsConflictResolver.java
  MdnsInterfaceManager.java
  DnsSdBrowser.java
src/test/java/.../cluster/dns/
  DnsSdRecordBuilderTest.java
  DnsSdServiceRecordTest.java
  MdnsPacketCodecTest.java
  MdnsResponderTest.java
  MdnsQuerierTest.java
  DnsSdDiscoveryIntegrationTest.java
  DnsSdConflictResolutionTest.java
  DnsSdMultiInterfaceTest.java
  DnsSdBrowserTest.java
demos/src/main/java/.../cluster/
  DnsSdDiscoveryDemo.java
```
