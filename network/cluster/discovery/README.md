
# Lego Flow Cluster Discovery — DNS-SD/mDNS

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-102_passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.2.0-SNAPSHOT-blue.svg)]()

Zero-configuration service discovery using DNS Service Discovery (DNS-SD) and Multicast DNS (mDNS) for automatic cluster formation. Implements RFC 6762 (mDNS) and RFC 8305 (DNS-SD).

## Overview

Nodes announce themselves on the local network via multicast DNS at `224.0.0.251:5353` and discover peers without any external infrastructure. Each node publishes a service record containing its identity, port, and metadata. Other nodes browse for services and build a membership table from the responses.

```
Node A                          mDNS Multicast (224.0.0.251:5353)                   Node B
  |                                                       |                                 |
  |-- Probe (3x) ---------------------------------------->|                                 |
  |<------------------------------------------------------| (no response — name free)
  |                                                       |                                 |
  |-- Announcement -------------------------------------->| (Node B hears)                |
  |                                                       |                                 |
  |                                                       |-- Query ---------------------->|
  |<------------------------------------------------------| Response (A record + SRV)      |
  |                                                       |  Node B adds Node A to table    |
```

## Key Classes

- **DnsSdDiscovery** — `ClusterMembership` implementation via DNS-SD/mDNS
- **MdnsResponder** — mDNS announcement, probe, and goodbye messages
- **MdnsQuerier** — Multicast DNS querier
- **DnsSdBrowser** — Multicast DNS service browser with TTL-based cache
- **MdnsPacketCodec** — Binary DNS packet encoder/decoder (RFC 1035 wire format)
- **MdnsConflictResolver** — Name conflict resolution (ASCII step-down per RFC 6762 §5)
- **MdnsInterfaceManager** — Multicast socket binding per network interface
- **DnsSdConfig** — Configuration for service registration
- **DnsSdServiceRecord** — Discovered service record (instance, type, port, TXT attributes)

## Quick Start

### Start Discovery

```java
var config = DnsSdConfig.builder()
    .instanceName("node-1")
    .serviceType("_legoflow")
    .port(8001)
    .txtAttributes(Map.of("role", "PRIMARY", "version", "0.2.0"))
    .build();

var discovery = new DnsSdDiscovery(config);
discovery.onEvent(event -> {
    switch (event) {
        case NodeJoined nj -> log.info("Discovered: {}", nj.node().id());
        case NodeLeft nl -> log.info("Lost: {}", nl.node().id());
    }
});

discovery.start(new DefaultContext());
```

### Manual Service Browser

```java
var browser = new DnsSdBrowser("_legoflow._tcp.local");
browser.start();
var services = browser.getRecords(); // List<DnsSdServiceRecord>
```

## Protocol Compliance

- **RFC 6762** (mDNS) — Probing, announcement, cache-flushing, goodbye packets, name conflict resolution
- **RFC 8305** (DNS-SD) — Service instance naming, PTR/SRV/TXT record structure, browse → resolve flow
- **RFC 1035** (DNS wire format) — Name compression, label encoding, RR format

## Dependencies

- **cluster-core** — membership SPI, event model, `ClusterNode`
- **dns** — DNS wire format utilities (reused for mDNS packets)
- **service** — lifecycle management
