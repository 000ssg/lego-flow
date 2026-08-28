# Phase 5: Unit Test Matrix

**Status:** Complete  
**Total tests:** 271  
**Failures:** 0  
**Errors:** 0  
**Skipped:** 0  
**Result:** BUILD SUCCESS

---

## Test coverage by module

| Module | Test class | Tests | Coverage target |
|--------|-----------|-------|-----------------|
| Types | `TypeCodecTest` | 64 | AMQP type encoding/decoding |
| Types | `DescriptorsTest` | 7 | Descriptor constants |
| Transport | `PerformativeCodecTest` | 37 | Performative encoding/decoding |
| Transport | `FrameCodecTest` | 16 | Frame encoding/decoding |
| Transport | `InMemoryTransportTest` | 6 | In-memory transport for testing |
| Message | `MessageCodecTest` | 21 | AMQP message encoding/decoding |
| SASL | `SaslTest` | 4 | SASL mechanism handling |
| Delivery | `DeliveryStateCodecTest` | 15 | Delivery state encoding |
| Link | `SenderLinkTest` | 16 | Sender link credit, state, disposition |
| Link | `ReceiverLinkTest` | 13 | Receiver link credit, receive, accept |
| Session | `AmqpSessionTest` | 17 | Session state, channel mapping |
| Container | `ContainerModeTest` | 9 | Mode defaults, config builder |
| Container | `AmqpContainerTest` | 1 | Container startup |
| Container | `AmqpContainerCoverageTest` | 7 | Container lifecycle coverage |
| Client | `BrokerModeTest` | 12 | Broker mode address formatting, settle modes |
| Client | `AmqpClientTest` | 4 | Client config, connection |
| Client | `AmqpClientCoverageTest` | 8 | Client flow coverage |
| Client Service | `AmqpClientServiceTest` | 6 | Spring service wiring |
| Server Service | `AmqpContainerServiceTest` | 7 | Spring service wiring |
| Common | `AmqpCommonTest` | 2 | Constants, state machine |
| **Total** | | **271** | |

---

## New tests added in this phase

- `ContainerModeTest` — validates all 5 ContainerMode profiles, config builder mode-aware defaults, override behavior
- `BrokerModeTest` — validates all 5 BrokerMode profiles, address formatting, settle modes, ClientConfig integration

## Tests from Phase 3

- Modified `SenderLinkTest.testSendWithNoCredit` → split into `testSendWithNoCreditSettled` and `testSendWithNoCreditUnsettled` to match auto-grant behavior for pre-settled transfers

## Lessons

- ContainerMode STANDARD proto0Accepted was false (typo) — fixed before tests
- SaslMechanisms is a String, not List — use `.split(",")` for element assertions
- 271 tests exercise the full stack: wire protocol, types, session, links, container, client
