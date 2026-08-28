# Phase 6: Interop Test Matrix
**Status:** ☐ Not started
**Started:** —
**Completed:** —

## Goal
Verify legoflow client against real reference servers (Docker-only).

## Test Matrix (reference servers × clients)

| Server (Docker) | Client | Tests | Notes |
|-----------------|--------|-------|-------|
| Artemis (arm64) | legoflow AmqpClient (AUTO) | T01-T18 | Primary validation |
| Artemis | legoflow AmqpClient (ARTEMIS) | T01-T18 | Vendor mode |
| Artemis | rhea.js | T01-T18 | Reference baseline |
| Qpid Dispatch (amd64/qemu) | legoflow AmqpClient (AUTO) | T01-T18 | Proton-C baseline |
| Qpid Dispatch | legoflow AmqpClient (QPID) | T01-T18 | Vendor mode |
| Qpid Dispatch | rhea.js | T01-T18 | Proton pair |
| RabbitMQ | legoflow AmqpClient (AUTO) | T01-T18 | Legacy broker |
| RabbitMQ | legoflow AmqpClient (RABBITMQ) | T01-T18 | Vendor mode |
| RabbitMQ | rhea.js | T01-T18 | Sanity check |

## Server Setup

| Server | Docker Image | Port | Auth | Arm64? |
|--------|-------------|------|------|--------|
| Artemis | `apache/artemis:latest-alpine` | 5672 | PLAIN, ANONYMOUS | ✅ |
| Qpid Dispatch | `scholzj/qpid-dispatch:latest` | 5672 | ANONYMOUS | ❌ (qemu) |
| RabbitMQ | `rabbitmq:4-management` | 5672 | PLAIN, ANONYMOUS | ✅ |

## Sub-tasks (chained)

### 6.1 Set up Docker servers
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 6.2 Run Artemis matrix (3 combinations)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 6.3 Run Qpid Dispatch matrix (3 combinations)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 6.4 Run RabbitMQ matrix (3 combinations)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 6.5 Document final interop matrix
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 6.6 Update COMPATIBILITY.md with verified results
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

## References
- Phase 5 unit tests: phase5-unit-tests.md
- Compatibility analysis: ../../doc/AMQP10_COMPATIBILITY.md
- Standard test suite T01-T18: phase5-unit-tests.md

## Summary doc: — (created after phase completion)
