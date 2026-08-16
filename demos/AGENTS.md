
# demos — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `demos` module contains end-to-end integration demos exercising cross-module scenarios. Demos validate that multiple protocol modules work together in realistic deployments.

## Key Notes

- **No COMPLIANCE.md required** — Demos validate integration, not spec compliance
- **Test coverage** — Demo tests verify multi-module scenarios (cluster, messaging, HTTP)
- **Flaky tests** — Some demos depend on timing (network, CoAP) and may timeout; these are documented
- **No source code published** — Demos are development/test assets, not library code

## Demo Categories

| Category | Description |
|----------|-------------|
| Cluster | Multi-node clustering (partition tolerance, cache coherence, leader election) |
| Messaging | Cross-protocol messaging (NATS, Kafka, AMQP, MQTT) |
| HTTP | Server-client integration, load balancing, sticky sessions |
| Protocol | Protocol-specific demos (SIP, RTP, DNS, Redis) |
