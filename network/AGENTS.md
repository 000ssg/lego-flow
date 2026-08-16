
# network — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `network` module is a parent POM (packaging=pom) that groups all network protocol sub-modules under a single build hierarchy.

## Module Structure

```
network/                         <- parent POM (lego-flow-network)
  common/                        <- BER/ASN.1 codec shared utilities
  dns/                           <- DNS protocol implementation
  ldap/                          <- LDAP v3 directory access
  snmp/                          <- SNMPv3 network management
  syslog/                        <- Syslog structured logging
  modbus/                        <- Modbus TCP industrial protocol
  ssh/                           <- SSH secure shell
  ftp/                           <- FTP file transfer
  cluster/                       <- Multi-node clustering (aggregator)
    core/                        <- Cluster membership, events, lifecycle
    discovery/                   <- DNS-SD/mDNS peer discovery
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-network (network/pom.xml)
      -> lego-flow-network-common (network/common/pom.xml)
      -> lego-flow-dns (network/dns/pom.xml)
      -> lego-flow-ldap (network/ldap/pom.xml)
      -> lego-flow-snmp (network/snmp/pom.xml)
      -> lego-flow-syslog (network/syslog/pom.xml)
      -> lego-flow-modbus (network/modbus/pom.xml)
      -> lego-flow-ssh (network/ssh/pom.xml)
      -> lego-flow-ftp (network/ftp/pom.xml)
      -> lego-flow-cluster (network/cluster/pom.xml)
          -> lego-flow-cluster-core (network/cluster/core/pom.xml)
          -> lego-flow-cluster-discovery (network/cluster/discovery/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| common | 9 |
| dns | 39 |
| ldap | 19 |
| snmp | 23 |
| syslog | 13 |
| modbus | 11 |
| ssh | 57 |
| ftp | 27 |
| cluster/core | 13 |
| cluster/discovery | 10 |

## Build Commands

```bash
# Build all network modules
mvn test -pl network/common,network/dns,network/ldap,network/snmp,network/syslog,network/modbus,network/ssh,network/ftp,network/cluster -am

# Build single module
mvn test -pl network/dns -am
```
