# Phase 4 Summary: Client Compatibility

**Status:** Complete  
**Date:** 2026-08-26  
**Tests:** 271 pass (all green)

## What was built

### BrokerMode enum (NEW)

5 broker target profiles with client-side adaptations:

| Mode | Address Format | Sender Settle | Receiver Settle | SASL Mechanism |
|------|---------------|---------------|-----------------|----------------|
| STANDARD | Queue name | unsettled(0) | first | PLAIN / ANONYMOUS |
| RABBITMQ | `amq.topic/exchange.queue` | unsettled(0) | first | ANONYMOUS |
| ARTEMIS | Queue name | unsettled(0) | first | PLAIN |
| QPID_DISPATCH | Queue name | unsettled(0) | first | ANONYMOUS |
| IBM_MQ | `queue-manager/queue-name` | unsettled(0) | unsettled(0) | PLAIN / ANONYMOUS |

### ClientConfig updates

- Added `brokerMode()` field (default: STANDARD)
- Added `brokerMode(BrokerMode)` builder method
- All defaults now sourced from selected `BrokerMode`

### AmqpClient connection flow (FIXED)

**Before:** Client sent proto-0 header for anonymous connections → RabbitMQ rejected with socket close

**After:** Three-path protocol negotiation:

1. Send SASL_HEADER → if server echoes SASL_HEADER → do SASL → AMQP_HEADER → OPEN
2. Send SASL_HEADER → if server responds AMQP_HEADER → skip SASL → OPEN
3. Send SASL_HEADER → if server closes socket → reopen, send AMQP_HEADER → OPEN (Qpid Dispatch path)

### AmqpClient link creation (FIXED)

**Before:**
- Sender settle mode hardcoded to `immediate(1)` (pre-settled)
- Receiver settle mode hardcoded to `immediate(1)` (pre-settled)

**After:**
- Sender uses `BrokerMode.senderSettleMode()` → `unsettled(0)` for RABBITMQ, ARTEMIS, QPID_DISPATCH
- Receiver uses `BrokerMode.receiverSettleMode()` → `first` for most, `unsettled(0)` for IBM_MQ

### AmqpClient address formatting (FIXED)

**Before:** Raw queue name sent as source/target address

**After:** `BrokerMode.formatAddress(address)` applied:
- RABBITMQ: prepends `amq.topic/`
- IBM_MQ: prepends `queue-manager/`
- Others: pass through

## Verified

- All 271 unit tests pass
- `BrokerMode` format conversions verified by test
- `ClientConfig` builder mode-aware defaults verified by test
- No regressions in existing test suite

## Continuity notes for Phase 5+

- `AmqpClient` uses `ClientConfig.brokerMode()` to configure settlement and address formatting
- `BrokerMode.formatAddress()` must be called on link addresses before sending ATTACH frames
- Connection flow now handles all 3 Docker brokers (RabbitMQ, Artemis, Qpid Dispatch)
- Qpid Dispatch fallback path reopens socket and skips SASL entirely
