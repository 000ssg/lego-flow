# messaging / xmpp — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `xmpp` module implements XMPP core (RFC 6120) and XMPP IM (RFC 6121) with IoT extensions for real-time communication and machine-to-machine interaction. It builds on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `XmppClient` — client with connect, authenticate, send/receive stanzas, presence, roster
- `XmppStream` — XML stream lifecycle: open, feature negotiation, close
- `StanzaRouter` — routes message, presence, iq stanzas to registered handlers
- `RosterManager` — contact list management with subscription state machine
- `PresenceEngine` — presence broadcast, directed presence, presence probes
- `IoTManager` — IoT extensions: sensor data (XEP-0323), control (XEP-0325), discovery (XEP-0347)
- `StreamManager` — XEP-0198 stanza acknowledgement and session resumption

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `stream` | XML stream lifecycle: stream open/close, feature negotiation, error stanzas, stream restart |
| `auth` | Authentication: SASL mechanisms (PLAIN, SCRAM-SHA-1, SCRAM-SHA-256), TLS/STARTTLS negotiation |
| `stanza` | Stanza model: message (chat, groupchat, headline), presence (available, away, dnd, xa), iq (get, set, result, error) |
| `roster` | Roster management: contact add/remove, subscription states (none, to, from, both), groups |
| `presence` | Presence engine: initial presence, directed presence, presence probes, priority-based routing |
| `muc` | Multi-User Chat (XEP-0045): room creation, join/leave, nick, role/affiliation, room messages |
| `pubsub` | Publish-Subscribe (XEP-0060): node creation/deletion, item publish, subscription management, notifications |
| `iot` | IoT extensions: sensor data read/subscribe (XEP-0323), control set/get (XEP-0325), thing registration/discovery (XEP-0347) |
| `sm` | Stream Management (XEP-0198): stanza counting, ack requests (<r/>/<a/>), session resumption, unacked queue |
| `demo` | Demo applications: simple client, presence, MUC, IoT sensor, IoT control |

## XMPP-Specific Coding Conventions

### JID (Jabber ID) Format
- Full JID: `user@domain/resource` (e.g., `alice@example.com/phone`)
- Bare JID: `user@domain` (e.g., `alice@example.com`)
- Domain JID: `domain` (e.g., `example.com`)

### Stanza Types
- `<message>` — types: chat, groupchat, headline, normal, error
- `<presence>` — types: available (default), unavailable, subscribe, subscribed, unsubscribe, unsubscribed, probe, error
- `<iq>` — types: get, set, result, error (must have unique id attribute)

### Stream Lifecycle
1. Open TCP connection
2. Send `<stream:stream>` opening tag
3. Receive server stream features
4. TLS negotiation (STARTTLS) and stream restart
5. SASL authentication and stream restart
6. Resource binding (`<iq type="set"><bind>`)
7. Session establishment (optional)
8. Stanza exchange
9. Close with `</stream:stream>`

### Subscription State Machine (Roster)
- `none` — no subscription in either direction
- `to` — subscribed to contact's presence
- `from` — contact subscribed to our presence
- `both` — mutual subscription

### IoT Extension Namespaces
- Sensor data: `urn:xmpp:iot:sensordata` (XEP-0323)
- Control: `urn:xmpp:iot:control` (XEP-0325)
- Discovery: `urn:xmpp:iot:discovery` (XEP-0347)

## Stream-Oriented Codec (XmppCodec)

XmppCodec accumulates incoming TCP data and uses regex matching to detect complete XML stanza boundaries. Key points:

- **Buffer cleanup**: uses `Matcher.end()` position tracking to trim processed stanzas from the buffer, replacing the previous fragile `lastIndexOf("</message>")` approach
- **hasBufferedData()**: checks whether the codec holds incomplete stanza data
- **Position tracking advantage**: works correctly for all stanza types (`<message>`, `<presence>`, `<iq>`), handles nested elements, and does not break when stanza content contains closing-tag substrings

This pattern is common across Lego Flow protocol codecs that operate over TCP byte streams (see also MqttCodec for a similar accumulation approach).

## Testing Practices

- Unit tests for XML stream parsing and generation
- SASL authentication tests: PLAIN, SCRAM-SHA-1, SCRAM-SHA-256 flows
- Stanza routing tests: message, presence, iq dispatch to correct handlers
- Roster tests: subscription state machine transitions
- Presence tests: initial presence, directed presence, probes
- IoT extension tests: sensor data read/subscribe, control set, device discovery
- Stream management tests: ack counting, resume after disconnect
- All tests use in-memory transport (no external XMPP server required)
- Test count: 268
