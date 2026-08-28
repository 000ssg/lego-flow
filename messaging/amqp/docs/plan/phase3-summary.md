# Phase 3 Summary: Server Fixes + Vendor Simulation

**Status:** Complete  
**Date:** 2026-08-26  
**Tests:** 271 pass (all green)

## What was built

### ContainerMode enum (NEW)

5 broker profiles with mode-aware defaults:

| Mode | SASL Required | Proto-0 | Authzid Empty | Channel Max | Idle Timeout | SASL Mechanisms |
|------|---------------|---------|---------------|-------------|--------------|-----------------|
| STANDARD | No | Yes | No | 65535 | Disabled | PLAIN, ANONYMOUS, EXTERNAL |
| RABBITMQ | Yes | No | Yes | 65535 | 60s | PLAIN, ANONYMOUS |
| ARTEMIS | No | Yes | No | 65535 | Disabled | PLAIN, ANONYMOUS, EXTERNAL, GSSAPI |
| QPID_DISPATCH | No | Yes | No | 32767 | 8s | ANONYMOUS |
| IBM_MQ | No | Yes | No | 65535 | Disabled | PLAIN, ANONYMOUS, EXTERNAL |

### ContainerConfig updates

- Added `mode(ContainerMode)` field and builder method
- All defaults now sourced from selected `ContainerMode`
- Builder applies mode defaults before user overrides

### AmqpContainer rewrites (FIXES APPLIED)

| Gap | Fix |
|-----|-----|
| **SASL-first negotiation** | Container handles both SASL-first clients AND proto-0 clients in the same accept loop |
| **authzid validation** | RABBITMQ mode rejects non-empty authzid; other modes accept any authzid |
| **Auto-accept transfers** | REMOVED — messages now stay in `pendingMessages` until handler calls accept/reject/release |
| **Settle mode defaults** | Server sends `unsettled(0)` as sender-settle-mode; uses `first` for receiver |
| **Address normalization** | `normalizeAddress()` converts QpidDispatch addresses to queue format |
| **Channel-max default** | QPID_DISPATCH mode limits to 32767; others use 65535 |
| **Idle timeout enforcement** | Idle timeout timer starts on connection; QPID_DISPATCH (8s) and RABBITMQ (60s) modes enforce it |

## State machine changes

Connection handling now supports two flows:

1. **SASL-first flow**: SASL_HEADER → sasl-mechanisms → sasl-init → sasl-outcome → AMQP_HEADER → OPEN
2. **Proto-0 flow**: AMQP_HEADER → OPEN (no SASL)

The `ConnectionContext.state` now includes `PROTO0_HDR` to distinguish proto-0 header reception from SASL mode.

## Verified

- All 271 unit tests pass
- `ContainerMode` default values verified by test
- `ContainerConfig` builder mode-aware defaults verified by test
- No regressions in existing test suite

## Continuity notes for Phase 4+

- `AmqpContainer` now supports `ContainerMode` — all phase 4+ tests should use mode-aware configs
- `ConnectionContext` has `state()` method that returns `ConnectionState` enum (PROTO0_HDR, SASL_MODE, SASL_DONE, HDR_EXCH, OPEN, BEGIN_SENT, BEGIN_RCVD, MAPPED, CLOSED)
- `pendingMessages` map replaces auto-accept behavior — test code must explicitly call `accept()` or `reject()`
