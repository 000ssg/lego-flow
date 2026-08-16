
# Lego Flow Network — Network Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for network protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [common](common/) | `lego-flow-network-common` | BER/ASN.1 codec utilities |
| [dns](dns/) | `lego-flow-dns` | DNS protocol (RFC 1034/1035) |
| [ldap](ldap/) | `lego-flow-ldap` | LDAP v3 (RFC 4511) |
| [snmp](snmp/) | `lego-flow-snmp` | SNMPv3 (RFC 3411-3418) |
| [syslog](syslog/) | `lego-flow-syslog` | Syslog (RFC 5424) |
| [modbus](modbus/) | `lego-flow-modbus` | Modbus TCP |
| [ssh](ssh/) | `lego-flow-ssh` | SSH (RFC 4251-4256) |
| [ftp](ftp/) | `lego-flow-ftp` | FTP (RFC 959) |
| [cluster](cluster/) | `lego-flow-cluster` | Multi-node clustering protocols |

## Test Coverage

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
| cluster | — (aggregator) |
| **Total** | **198+** |

## Build Commands

```bash
# Build all network modules
mvn test -pl network/common,network/dns,network/ldap,network/snmp,network/syslog,network/modbus,network/ssh,network/ftp,network/cluster -am

# Gradle
./gradlew :network:dns:test :network:ssh:test
```
