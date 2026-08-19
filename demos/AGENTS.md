
# demos — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.

## Module Purpose

The `demos` module contains end-to-end integration demos exercising cross-module scenarios. Demos validate that multiple protocol modules work together in realistic deployments.

## Demo Convention

**All protocol module demos live in this central `demos` module, not in individual modules.**

### Directory Structure
Demos are organized by protocol area using sub-packages that mirror the source module paths:

```
demos/
├── src/main/java/ssg/legoflow/
│   ├── blocks/demo/              — blocks module demos
│   ├── database/redis/demo/      — redis module demos
│   ├── ftp/demo/                 — ftp module demos
│   ├── network/
│   │   ├── terminals/base/demo/  — terminals-base demos
│   │   ├── terminals/vt52/demo/  — VT52 terminal demos
│   │   ├── terminals/vt100/demo/ — VT100 terminal demos
│   │   ├── terminals/...         — all terminal type demos
│   │   ├── telnet/base/demo/     — telnet-base demos
│   │   └── telnet/gateway/demo/  — telnet-gateway demos
│   └── ...
└── src/test/java/ssg/legoflow/   — mirrored test packages
```

### Rules
1. **Never place demo classes inside individual protocol modules** — demos belong in `demos/` only
2. **Package names mirror the source module** — e.g., `ssg.legoflow.network.terminals.vt100.demo`
3. **Each demo has a main class and a test class** — `TerminalDemo.java` and `TerminalDemoTest.java`
4. **Demo tests use Given/When/Then style** and follow project test conventions (AssertJ, no Thread.sleep)
5. **No demo class should be committed to individual module `src/main/java/*/demo/`** — use `demos/` instead

### Dependencies
Add new module dependencies to `demos/build.gradle.kts` in the appropriate category section.

## Demo Categories

| Category | Description |
|----------|-------------|
| Cluster | Multi-node clustering (partition tolerance, cache coherence, leader election) |
| Messaging | Cross-protocol messaging (NATS, Kafka, AMQP, MQTT) |
| HTTP | Server-client integration, load balancing, sticky sessions |
| Protocol | Protocol-specific demos (SIP, RTP, DNS, Redis, Terminals, Telnet) |
| Database | Redis, PostgreSQL, MySQL demos |
| Auth | GSSAPI, OAuth, SSO demos |

## Key Notes

- **No COMPLIANCE.md required** — Demos validate integration, not spec compliance
- **Test coverage** — Demo tests verify multi-module scenarios
- **Flaky tests** — Some demos depend on timing (network, CoAP) and may timeout; these are documented
- **No source code published** — Demos are development/test assets, not library code
