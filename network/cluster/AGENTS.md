
# network / cluster — Aggregator Module

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

This is a Maven POM aggregator module for the cluster protocol suite. It groups the cluster sub-modules (`core`, `discovery`) under a common parent. Build files, source code, and tests are in the sub-modules.

## Sub-modules

| Module | Description |
|--------|-------------|
| [core](core/) | Foundational abstractions: membership SPI, events, lifecycle, consistent hashing |
| [discovery](discovery/) | DNS-SD/mDNS zero-config peer discovery (RFC 6762/8305) |

## Notes

- No source code or tests in this aggregator — all code is in sub-modules
- `pom.xml` declares `<packaging>pom</packaging>` with child modules
- Related service-level module: `service/cluster-coordination` (etcd/Raft)
