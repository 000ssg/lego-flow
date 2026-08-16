
# network / cluster / discovery — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `cluster-discovery` module implements zero-configuration service discovery using DNS Service Discovery (DNS-SD) and Multicast DNS (mDNS). Nodes announce themselves on the local network and discover peers without any external infrastructure.

## Key Interfaces

### DnsSdDiscovery
Main entry point implementing `ClusterMembership`. Combines an `MdnsResponder` (for announcing) and a `DnsSdBrowser` (for discovering peers). Maps discovered `DnsSdServiceRecord` instances to `ClusterNode` objects.

### MdnsResponder
Handles mDNS announcement, probe, and goodbye messages. Implements the three-phase announcement sequence (probe → announce → periodic refresh) per RFC 6762. Uses `MdnsPacketCodec` for binary DNS packet encoding/decoding.

### DnsSdBrowser
Sends multicast DNS queries and collects responses. Maintains a cache of discovered services with TTL tracking, refreshing entries before expiry and removing stale entries.

### MdnsPacketCodec
Binary DNS packet encoder/decoder following RFC 1035 wire format. Handles name compression (0xC0 pointers), label encoding, and RR format.

### MdnsConflictResolver
Handles name conflicts following RFC 6762 §5 step-down procedure for ASCII names. Appends `-local`, `-local-2`, etc. when conflicts are detected.

### MdnsInterfaceManager
Manages multicast socket binding per network interface, enabling cross-subnet discovery.

## Protocol-Specific Conventions

### mDNS Wire Format
- Multicast address: `224.0.0.251`
- UDP port: `5353`
- Cache-flushing bit: top bit of TTL (0x8000_0000)
- Name conflicts: ASCII step-down procedure

### Service Naming
- Instance FQDN: `Instance.type.domain` (e.g., `node-1._legoflow._tcp.local`)
- Service type pattern: `_Service._Proto` (e.g., `_legoflow._tcp`)
- PTR → SRV → TXT resolution chain

### Packet Types
- **Probe** — 3 sequential queries to check if a name is available
- **Announcement** — Asserts ownership after successful probe
- **Goodbye** — TTL=0 packet sent on graceful leave
- **Query** — PTR query for discovering services

## Design Decisions

- **Per-interface binding** — Supports multiple network interfaces via `MdnsInterfaceManager`
- **Cached records** — Browser caches discovered services with TTL tracking
- **Conflict resolution** — Follows RFC 6762 §5 step-down procedure
- **Transport independence** — Uses raw `MulticastSocket`, independent of `ClusterTransport` SPI
- **Event mapping** — DNS-SD events (appeared, vanished) mapped to `ClusterEvent` hierarchy

## Thread Safety

- Multicast socket operations are synchronized
- Browser cache uses `ConcurrentHashMap` for discovered records
- Event listeners use `CopyOnWriteArrayList`

## Package Structure

```
ssg.legoflow.network.cluster.dns/
  DnsSdConfig            — Configuration for DNS-SD registration
  DnsSdDiscovery         — ClusterMembership implementation via DNS-SD/mDNS
  DnsSdBrowser           — Multicast DNS service browser
  DnsSdServiceRecord     — Discovered service record
  DnsSdRecordBuilder     — Builds DNS records for registration
  MdnsResponder          — mDNS responder (announce, probe, goodbye)
  MdnsQuerier            — Multicast DNS querier
  MdnsPacketCodec        — Binary DNS packet encoder/decoder
  MdnsConflictResolver   — Name conflict resolution
  MdnsInterfaceManager   — Multicast socket per interface
```

## Testing

- **Framework**: JUnit 5 + AssertJ
- **102 tests passing**
- Integration test (`DnsSdDiscoveryIntegrationTest`) validates end-to-end discovery flow
- Packet codec tests validate RFC 1035 compliance

## Dependencies

- **cluster-core** — membership SPI, event model, `ClusterNode`
- **dns** — DNS wire format utilities (reused for mDNS)
- **service** — lifecycle management
