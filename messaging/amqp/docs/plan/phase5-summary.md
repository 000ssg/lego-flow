# Phase 5 Summary: Unit Test Matrix

**Status:** Complete  
**Date:** 2026-08-26  
**Total tests:** 271 | **Failures:** 0 | **Errors:** 0 | **Skipped:** 0 | **Result:** BUILD SUCCESS

## Test inventory by module

| Module | Test Class | Count | Coverage |
|--------|-----------|-------|----------|
| **Types** | `TypeCodecTest` | 64 | AMQP type encode/decode (Bool, Int, Long, ULong, String, Symbol, Binary, List, Map, Timestamp, UUID, Null, Described) |
| **Types** | `DescriptorsTest` | 7 | Descriptor constant values |
| **Transport** | `PerformativeCodecTest` | 37 | All performative encode/decode (Open, Begin, Attach, Flow, Transfer, Disposition, Release, Modify, Detach, End, Close, SaslMechanisms, SaslInit, SaslOutcome, Header, DeliveryAnnotations, MessageAnnotations, Properties, ApplicationProperties, Footer, Source, Target) |
| **Transport** | `FrameCodecTest` | 16 | Frame header, encoding, decoding, size calculation |
| **Transport** | `InMemoryTransportTest` | 6 | In-memory transport for unit tests |
| **Message** | `MessageCodecTest` | 21 | Message encode/decode, all section types |
| **SASL** | `SaslTest` | 4 | PLAIN, ANONYMOUS, GSSAPI mechanism encoding |
| **Delivery** | `DeliveryStateCodecTest` | 15 | Accepted, Released, Rejected, Modified, StateDescribed encode/decode |
| **Link** | `SenderLinkTest` | 16 | Credit flow, state transitions, settle modes, disposition |
| **Link** | `ReceiverLinkTest` | 13 | Credit flow, receive, accept/reject/release/modify |
| **Session** | `AmqpSessionTest` | 17 | State machine, channel mapping, link management |
| **Container** | `ContainerModeTest` | 9 | All 5 mode profiles + config builder defaults |
| **Container** | `AmqpContainerTest` | 1 | Container startup |
| **Container** | `AmqpContainerCoverageTest` | 7 | Connection lifecycle, SASL, OPEN, session |
| **Client** | `BrokerModeTest` | 12 | All 5 broker modes + address format + settle mode + config builder |
| **Client** | `AmqpClientTest` | 4 | Config, connection, session |
| **Client** | `AmqpClientCoverageTest` | 8 | Client flow coverage |
| **Client Service** | `AmqpClientServiceTest` | 6 | Spring service wiring |
| **Server Service** | `AmqpContainerServiceTest` | 7 | Spring service wiring |
| **Common** | `AmqpCommonTest` | 2 | Constants, state machine |

## New tests added this phase

- `ContainerModeTest` — validates all 5 ContainerMode profiles, mode-aware config builder, override behavior
- `BrokerModeTest` — validates all 5 BrokerMode profiles, address formatting per broker, settle mode defaults, config builder integration

## Fixes during this phase

- `ContainerMode.STANDARD.proto0Accepted` was `false` (typo) — fixed to `true` before tests
- `saslMechanisms` is a comma-delimited String — test assertions use `.split(",")` for element checks
- AssertJ `contains()` on String checks substring — fixed to use `.split(",")` pattern

## Continuity notes for Phase 6+

- All 271 tests pass — the full stack is exerciseable in unit test mode
- `InMemoryTransport` tests cover the core protocol without network
- Phase 6 (Interop) will test against real Docker brokers
- No infrastructure changes needed — just run `mvn test -Dtest='BrokerInteropTest'`
