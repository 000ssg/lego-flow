# CoAP Module — Requirements

## Timeline Overview

- **Module Added**: May 2026
- **Tests**: 156
- **Dependencies**: blocks (DP/DF), service (UDP transport)
- **Standards**: CoAP (RFC 7252), Observe (RFC 7641), Block (RFC 7959), Link Format (RFC 6690)

---

## Requirements

### Message Layer
1. Encode and decode CoAP messages in compact binary format (4-byte base header)
2. Support four message types: Confirmable (CON), Non-confirmable (NON), Acknowledgement (ACK), Reset (RST)
3. Generate and track 16-bit Message IDs for CON/NON messages
4. Generate and match tokens (0-8 bytes) for request/response correlation
5. Parse and serialize options in TLV format with delta encoding
6. Distinguish critical vs elective options, proxy-unsafe vs safe-to-forward

### Request/Response Model
1. Support REST methods: GET (0.01), PUT (0.03), POST (0.02), DELETE (0.04)
2. Map URI to options: Uri-Host, Uri-Port, Uri-Path, Uri-Query
3. Generate response codes in class.detail format (2.xx, 4.xx, 5.xx)
4. Support piggybacked responses (ACK carrying response) for efficiency
5. Support separate responses (empty ACK, then CON response) for slow resources
6. Content-Format negotiation via Content-Format and Accept options

### Observe (RFC 7641)
1. Register observers via GET with Observe option = 0
2. Deregister observers via GET with Observe option = 1 or RST to notification
3. Maintain observer registry per resource with notification sequence numbers
4. Deliver notifications as NON or CON messages based on resource configuration
5. Remove observers on notification delivery failure or max-age expiry
6. Order notifications by observe sequence number to handle reordering

### Blockwise Transfer (RFC 7959)
1. Block2 option for blockwise response payloads (server-to-client)
2. Block1 option for blockwise request payloads (client-to-server)
3. Negotiate block size (16 to 1024 bytes in powers of 2) via SZX field
4. Track block number (NUM field) and more-blocks flag (M field)
5. Assemble complete payload from sequential blocks
6. Handle early negotiation (client proposes size, server may reduce)

### Resource Discovery (RFC 6690)
1. Serve /.well-known/core resource returning CoRE Link Format
2. Generate link descriptions with target URI, resource type (rt), interface (if), content type (ct)
3. Parse incoming link format for client-side discovery
4. Support filtering by resource type and interface in discovery queries
5. Auto-register resources with discovery endpoint

### Reliability and Congestion Control
1. Retransmit CON messages with exponential backoff (initial ACK_TIMEOUT = 2s, ACK_RANDOM_FACTOR = 1.5)
2. Maximum MAX_RETRANSMIT attempts (default 4) before declaring failure
3. Deduplicate received messages by Message ID (cache for EXCHANGE_LIFETIME)
4. Limit concurrent outstanding interactions to NSTART (default 1) per endpoint
5. Track and respect MAX_TRANSMIT_SPAN and MAX_TRANSMIT_WAIT timers

### Server
1. Bind to UDP port (default 5683) and process incoming requests
2. Register resources with URI path patterns and handler callbacks
3. Dispatch requests to matching resource handlers
4. Build and send responses with appropriate codes and options
5. Support multicast request reception (group communication)

### Client
1. Construct and send CoAP requests with method, URI, options, payload
2. Match responses to requests by token
3. Manage observe subscriptions with automatic deregistration
4. Handle blockwise transfer transparently for large payloads
5. Provide synchronous and asynchronous request APIs

### Demo Applications
1. SimpleServerDemo: CoAP server with temperature and LED resources
2. ClientRequestDemo: GET/PUT/POST/DELETE with content formats
3. ObserveDemo: observe resource with push notifications
4. BlockTransferDemo: large payload transfer with blockwise options
5. DiscoveryDemo: /.well-known/core discovery and link parsing

---

## Commit: `pending` - Fix CoAP flaky tests with ephemeral ports (2026-07-05)

### Original Request
> "fix the CoAP flaky test(s), e.g. by using non-conflicting resources to avoid conflicts with parallel resources"

### Reformulated Requirements
1. Eliminate port conflicts when CoAP tests run in parallel by using OS-assigned ephemeral ports instead of hardcoded ports
2. Add `getPort()` method to `CoapServer` to retrieve the actual bound port after binding to port 0
3. All test clients must connect to the dynamically assigned port via `server.getPort()`

### Final Design Decisions
- **Ephemeral ports (port 0)**: All 6 test files changed from hardcoded ports (15683, 15700-15705) to port 0, letting the OS assign a free port per test. This eliminates `BindException` when tests run in parallel across modules.
- **`CoapServer.getPort()`**: New public method reads the actual local port from the bound `DatagramChannel`. Falls back to configured port if channel is not yet bound. Updated server log message to show actual port.
- **No test logic changes**: Only port allocation changed; all assertions and test scenarios remain identical.

### Implementation Details
- **CoapServer.java** — added `getPort()` method, updated log to show actual port
- **CoapClientTest.java** — PORT 15683→0, client uses `server.getPort()`
- **SimpleServerDemoTest.java** — PORT 15700→0, client uses `demo.server().getPort()`
- **ObserveDemoTest.java** — PORT 15701→0
- **BlockTransferDemoTest.java** — PORT 15702→0
- **IoTGatewayDemoTest.java** — PORT 15704→0
- **CoapRestDemoTest.java** — PORT 15705→0

### Test Coverage
- All 156 CoAP tests pass (no changes to test logic)
- All 8,136 project-wide tests pass (no regressions)
- Port conflict flakiness eliminated

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~5 min |
| Files created/modified | 7 |
| Lines added/removed | +15 / -7 |
| Tests added | 0 (total: 156) |

---

## Commit: `TBD` - DemoCoapAll Comprehensive Demo (2026-07-07)

### Original Request
> "Create DemoAll classes for iot/coap module following the pattern from messaging/mqtt. DemoCoapAll.java in src/main/java demo package, DemoCoapAllTest.java in src/test/java demo package. Cover all major features: GET/PUT/POST/DELETE, observe, discovery, content format, IoT gateway, block transfer."

### Reformulated Requirements
1. Create `DemoCoapAll` with `USE_EXTERNAL` flag, `Results` record, `runAll()`, individual demo methods
2. Cover 9 features: GET sensor, PUT sensor, POST item, DELETE item, observe, discovery, content format, IoT gateway, block transfer
3. Use existing `SimpleServerDemo` for GET/PUT, separate `CoapServer` for other features
4. Use `.equals()` for `CoapCode` comparisons (CoapCode is a final class, not a record)
5. Create `DemoCoapAllTest` with AssertJ assertions on each Results field
6. Javadoc on all public classes/methods with @since 0.1.0

### Final Design Decisions
- Reuse `SimpleServerDemo` for basic GET/PUT operations (proven pattern)
- Create second `CoapServer` on ephemeral port 0 for POST/DELETE/observe/discovery resources
- Use `CoapCode.CHANGED.equals(response.code())` instead of `==` because CoapCode is a final class and decoded responses create new instances

### Implementation Details
- `iot/coap/src/main/java/ssg/legoflow/coap/demo/DemoCoapAll.java` — 9 demo methods covering all CoAP features
- `iot/coap/src/test/java/ssg/legoflow/coap/demo/DemoCoapAllTest.java` — single test verifying all Results fields

### Test Coverage
- 1 new test added (DemoCoapAllTest.testAllFeatures)
- Total: 157 tests passing

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~50K |
| Agent tool calls | ~30 |
| Agent wall time | ~15 min |
| Files created/modified | 4 |
| Lines added/removed | +350 / -0 |
| Tests added | 1 (total: 157) |

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
