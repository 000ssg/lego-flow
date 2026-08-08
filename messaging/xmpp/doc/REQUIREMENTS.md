# XMPP Module — Requirements

## Timeline Overview

- **Module Added**: May 2026
- **Tests**: 162
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: XMPP Core (RFC 6120), XMPP IM (RFC 6121), XEP-0045, XEP-0060, XEP-0198, XEP-0323, XEP-0325, XEP-0347

---

## Requirements

### XML Stream (RFC 6120)
1. Establish XML stream with `<stream:stream>` opening tag and namespace declarations
2. Negotiate stream features: TLS (STARTTLS), SASL authentication, resource binding
3. Perform stream restart after TLS and SASL negotiation
4. Handle stream errors with defined error conditions (e.g., host-unknown, not-authorized)
5. Close stream gracefully with `</stream:stream>` closing tag
6. Parse incremental XML (streaming SAX/StAX) for stanza boundaries

### Authentication (SASL)
1. Support SASL PLAIN mechanism for simple username/password authentication
2. Support SASL SCRAM-SHA-1 mechanism with challenge/response and server verification
3. Support SASL SCRAM-SHA-256 for stronger hashing
4. Negotiate mechanism selection based on server-advertised mechanisms
5. Handle authentication success and failure stanzas
6. Perform TLS negotiation (STARTTLS) before SASL when required by server

### Stanza Processing
1. Route `<message>` stanzas by type: chat, groupchat, headline, normal, error
2. Route `<presence>` stanzas by type: available, unavailable, subscribe, subscribed, unsubscribe, unsubscribed, probe, error
3. Route `<iq>` stanzas by type: get, set, result, error; enforce mandatory id attribute
4. Match iq result/error to pending requests by id
5. Generate error stanzas for unhandled iq requests
6. Support stanza extension elements via registered namespace handlers

### Roster Management (RFC 6121)
1. Retrieve roster on login via `<iq type="get"><query xmlns="jabber:iq:roster"/>`
2. Add contacts with `<iq type="set">` containing roster item
3. Remove contacts by setting subscription to "remove"
4. Track subscription states per contact: none, to, from, both
5. Handle roster push notifications from server
6. Organize contacts into named groups

### Presence (RFC 6121)
1. Send initial presence on login to signal availability
2. Support show values: chat, away, xa, dnd (and default available)
3. Support status text and priority for resource selection
4. Handle directed presence to specific JIDs
5. Process presence probes from contacts with "from" subscription
6. Track contact availability based on received presence stanzas

### Multi-User Chat (XEP-0045)
1. Discover available rooms via service discovery
2. Join rooms with nick, receive room history and occupant list
3. Send and receive room messages (groupchat type)
4. Leave rooms with optional status message
5. Create and configure rooms (room name, description, access)
6. Manage occupant roles (moderator, participant, visitor) and affiliations (owner, admin, member, outcast)

### Publish-Subscribe (XEP-0060)
1. Create and delete pubsub nodes with configuration
2. Publish items to nodes with optional item IDs
3. Subscribe and unsubscribe to nodes
4. Receive event notifications for published/retracted items
5. Retrieve items from node (last published, specific item IDs)
6. Support node access models: open, presence, roster, authorize, whitelist

### IoT Sensor Data (XEP-0323)
1. Request momentary sensor data from a device via `<iq type="get">`
2. Receive sensor data response with typed fields (numeric, string, boolean, dateTime, enum)
3. Include field metadata: name, value, unit, data type, writable flag
4. Support historical data requests with time range
5. Subscribe to periodic sensor data updates
6. Handle `<accepted>`, `<started>`, `<fields>`, `<done>` sequence for async reads

### IoT Control (XEP-0325)
1. Send control commands via `<iq type="set">` with parameter name/value pairs
2. Support typed parameters: boolean, int, long, double, string, dateTime
3. Receive control response with success/failure status
4. Handle form-based control for complex parameter sets
5. Validate parameter types and ranges on the device side

### IoT Discovery (XEP-0347)
1. Register IoT things with registry (JID, node ID, source ID, tags)
2. Search for things by tag values and metadata
3. Claim discovered things for provisioning
4. Handle friendship requests between things
5. Support thing disownment and unregistration

### Stream Management (XEP-0198)
1. Enable stream management after authentication via `<enable>`
2. Track outgoing stanza count (sequence number)
3. Request acknowledgement via `<r/>` and respond with `<a h="N"/>`
4. Queue unacknowledged stanzas for potential retransmission
5. Resume session after disconnect using `<resume>` with previous stream ID
6. Redeliver unacknowledged stanzas on successful resumption

### Demo Applications
1. SimpleClientDemo: connect, authenticate, send/receive messages
2. PresenceDemo: presence management with roster and status updates
3. MultiUserChatDemo: create/join rooms, send room messages, manage occupants
4. IoTSensorDemo: register sensor, publish data, respond to read requests
5. IoTControlDemo: register actuator, handle control commands, confirm actions

---

## Commit: `TBD` - Stream-Oriented ByteBuffer Codec (2026-07-06)

### Original Request
> "check all protocol implementations for correct handling of data split over multiple data buffers"

### Reformulated Requirements
1. XmppCodec must correctly handle XML stanzas split across multiple TCP reads (ByteBuffer stream semantics)
2. Buffer cleanup after successful stanza extraction must use precise position tracking, not fragile string search

### Final Design Decisions
- Fixed buffer cleanup in XmppCodec to use `Matcher.end()` position tracking instead of fragile `lastIndexOf("</message>")` approach
- Added `hasBufferedData()` to check whether the codec holds incomplete stanza data
- Position-based tracking is more robust: it works correctly for all stanza types (not just `</message>`), handles edge cases with nested elements, and does not break when stanza content contains substrings like `</message>`

### Implementation Details
- Modified `XmppCodec.java`: replaced `lastIndexOf("</message>")` buffer cleanup with `Matcher.end()` position tracking, added `hasBufferedData()`

### Test Coverage
- No new tests added (total: 267)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (XMPP/RTSP/SIP codec fix) |
| Agent tokens | ~53K |
| Agent tool calls | ~29 |
| Agent wall time | ~3 min |
| Files created/modified | 1 (XmppCodec) |
| Lines added/removed | +30 / -21 |
| Tests added | 0 (total: 267) |

---

## Commit: `TBD` - DemoXmppAll Comprehensive Demo (2026-07-07)

### Original Request
> "Create DemoAll classes for messaging/xmpp module following the pattern from messaging/mqtt. DemoXmppAll.java in src/main/java demo package, DemoXmppAllTest.java in src/test/java demo package. Cover all major features: messaging, presence, roster, IoT sensor, IoT control, IoT discovery, smart home, pubsub."

### Reformulated Requirements
1. Create `DemoXmppAll` with `USE_EXTERNAL` flag, `Results` record, `runAll()`, individual demo methods
2. Cover 8 features: messaging, presence, roster, IoT sensor, IoT control, IoT discovery, smart home, pubsub
3. Reuse existing demos: SimpleChatDemo, PresenceDemo, RosterDemo, IoTSensorDemo, IoTControlDemo, IoTDiscoveryDemo, SmartHomeDemo
4. PubSub demo creates XmppClient directly with node creation, subscription, and item publishing
5. Create `DemoXmppAllTest` with AssertJ assertions on each Results field
6. Javadoc on all public classes/methods with @since 0.1.0

### Final Design Decisions
- Reuse existing demo classes for all features except PubSub
- PubSub demo uses XmppClient directly with PubSubManager for node/subscription/publish operations
- In-memory transport used by default; `USE_EXTERNAL` flag for real XMPP server testing

### Implementation Details
- `messaging/xmpp/src/main/java/ssg/legoflow/xmpp/demo/DemoXmppAll.java` — 8 demo methods covering all XMPP features
- `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/demo/DemoXmppAllTest.java` — single test verifying all Results fields

### Test Coverage
- 1 new test added (DemoXmppAllTest.testAllFeatures)
- Total: 268 tests passing

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~50K |
| Agent tool calls | ~30 |
| Agent wall time | ~15 min |
| Files created/modified | 4 |
| Lines added/removed | +360 / -0 |
| Tests added | 1 (total: 268) |

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
