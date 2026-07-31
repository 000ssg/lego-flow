# Lego Flow Development Guide

This document describes the development practices, patterns, and conventions used in the Lego Flow project.

## Project Overview

**Lego Flow** is a composable data processing framework for Java built on JDK 25. It provides layered abstractions for building data-driven services: composable blocks (DP/DF), service orchestration, HTTP protocol, web services, and WAMP protocol support.

### ⚠️ CRITICAL: YAML Multi-Line Commands in GitHub Actions — FOLDING BUG

**This bug has caused 3+ CI failures. ALWAYS follow this rule when editing `.github/workflows/*.yml`:**

**NEVER use backslash line continuations (`\\`) in `run:` blocks.** Always write multi-line
shell commands on a single line.

#### Why It Breaks:
GitHub Actions uses YAML implicit folding (no block scalar `|`). Folding converts newlines to
spaces but **keeps backslashes literally**. So bash sees `\` + space as an *escaped space*,
making the next argument start with a literal space character:

```yaml
# ❌ BROKEN — YAML folding converts this to:
# mvn -B verify " --no-daemon" "-DskipInteropTests=false" ...
run: mvn -B verify \
  --no-daemon \          # ← becomes ' --no-daemon' (leading space!)
  -DskipInteropTests=false

# ✅ CORRECT — single line, no backslashes:
run: mvn -B verify --no-daemon -DskipInteropTests=false ...
```

#### Symptom (what CI reports):
```
Unknown lifecycle phase " --no-daemon". You must specify a valid lifecycle phase...
Task ' --no-daemon' not found in root project 'lego-flow'...
```

**Affected commits**: 9c83b88, 33b5830, b4f10b5, 39fd980 — the same bug reappeared every time
CI commands were reformatted with multi-line YAML.

#### Safe Alternatives for Very Long Commands:
1. **Single line** (preferred when under ~200 chars): `run: ./gradlew task1 task2 -Pflag=value ...`
2. **Shell heredoc**: Use `shell: bash` with a `$'...'` quoting style
3. **Environment variable**: Set command in env block, then reference `${{ env.CMD }}`

**When editing any CI workflow file, scan ALL `run:` blocks for backslash continuations and
convert them to single lines.**

---

## Development Practices

### 1. Requirements Documentation

**Primary Rule:** All requirements, design decisions, and their evolution MUST be tracked in `doc/REQUIREMENTS.md`.

#### When Adding Features:
1. **Document Original Request**: Add verbatim user request at the start of each commit section
2. **Reformulate Requirements**: List clear, specific technical requirements
3. **Final Design Decisions**: Document what was chosen and why
4. **Implementation Details**: List files changed, features added, performance metrics
5. **Test Coverage**: Document tests added and total test count

#### REQUIREMENTS.md Structure:
```markdown
## Commit: `<hash>` - <Feature Name> (Date)

### Original Request
> "verbatim user request from conversation or discussion transcript"

### Reformulated Requirements
1. Specific technical requirement
2. Another requirement
...

### Final Design Decisions
- Architectural choices with rationale
- Trade-offs considered

### Implementation Details
- Files modified/created
- Key features implemented

### Test Coverage
- New tests added
- Total tests passing
```

### 1b. Cost Estimate Documentation

**Every commit** must include a `### Cost Estimate` section in the module's `doc/REQUIREMENTS.md` entry:

```markdown
### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | N (list names) |
| Agent tokens | ~X |
| Agent tool calls | ~Y |
| Agent wall time | ~Z min |
| Files created/modified | N |
| Lines added/removed | +A / -B |
| Tests added | N (total: M) |
```

This section goes after `### Test Coverage` in each commit's REQUIREMENTS.md entry.

### 2. Architecture Documentation

**ARCHITECTURE.md** (`doc/ARCHITECTURE.md`) documents the **current** set of architectural decisions.

- **Mandatory update** on every commit with architectural changes
- Unlike REQUIREMENTS.md (append-only, historical), ARCHITECTURE.md is **edited in place** to reflect the latest state
- Sections: module purpose, key abstractions, design patterns, data flow, dual API approach, extension points, thread safety model

### 3. Git Commit Practices

#### Commit Message Format:
```
<Title: Brief summary (max 72 chars)>

<Detailed description of changes>

- Bullet points for key changes
- Implementation highlights
- Test additions

Co-Authored-By: AI assistant
```

#### Commit Workflow:
1. Stage changes: `git add <files>`
2. Commit with detailed message using heredoc for proper formatting
3. Always include `Co-Authored-By: AI assistant`
4. **Update doc/REQUIREMENTS.md** with commit documentation
5. **Update doc/ARCHITECTURE.md** if architectural changes were made
6. **Update README.md**: reflect any API changes, new features, updated module structure, version badges, test counts
7. **NEVER run `git push` automatically.** Inform the user and wait for explicit instruction.

> **MANDATORY DOCUMENTATION RULE**: Steps 4–6 are not optional. Every commit must include up-to-date versions of REQUIREMENTS.md, README.md, and ARCHITECTURE.md (if architecture changed).

### 4. Document Locations

- `README.md` — module root directory (e.g., `blocks/README.md`)
- `AGENTS.md` — module root directory (e.g., `blocks/AGENTS.md`)
- `doc/REQUIREMENTS.md` — in `<module>/doc/` directory
- `doc/ARCHITECTURE.md` — in `<module>/doc/` directory

### 5. Code Organization

#### Module Layout:
```
lego-flow/                         ← root POM (1.0.0-SNAPSHOT, packaging=pom)
├── blocks/                        ← core DP/DF data processing framework
├── service/                       ← service-oriented framework using blocks (TCP + UDP)
├── web/                           ← Web & HTTP protocols
│   ├── http/                      ← HTTP/HTTPS protocol (RFC 2616)
│   ├── http2/                     ← HTTP/2 protocol (RFC 7540/RFC 9113)
│   ├── http3/                     ← HTTP/3 protocol (RFC 9114) over QUIC (RFC 9000)
│   ├── web-services/              ← web service components for HTTP
│   └── http-proxy/                ← HTTP forward/reverse/caching proxy (RFC 7230 §5.7)
├── iot/                           ← Internet of Things & device discovery
│   ├── upnp/                      ← UPnP/DLNA (SSDP, SOAP, GENA, media server/renderer/control point)
│   └── coap/                      ← CoAP (RFC 7252, observe, blockwise, resource discovery)
├── auth/                          ← Authentication parent module
│   ├── gssapi/                    ← GSS-API / Kerberos V5 / SPNEGO primitives (shared)
│   └── http-auth/                 ← HTTP authentication (5 sub-modules)
│       ├── core/                  ← Auth framework, sessions, JWT from scratch
│       ├── basic-digest/          ← HTTP Basic (RFC 7617) + Digest (RFC 7616)
│       ├── oauth/                 ← OAuth 2.0, PKCE, OpenID Connect, provider templates
│       ├── sso/                   ← Reverse proxy SSO, SAML assertion parsing
│       └── spnego/                ← HTTP Negotiate / SPNEGO (RFC 4559)
├── messaging/                     ← Message brokers & event streaming
│   ├── kafka/                     ← Apache Kafka wire protocol
│   ├── amqp/                      ← AMQP 1.0 (ISO 19464)
│   ├── stomp/                     ← STOMP 1.2
│   ├── nats/                      ← NATS core + JetStream
│   ├── mqtt/                      ← MQTT v3.1.1/v5.0 (pub/sub, QoS, broker + client)
│   ├── xmpp/                      ← XMPP (RFC 6120/6121, presence, messaging, IoT extensions)
│   └── wamp/                      ← WAMP protocol (invariant core + WebSocket adapter)
├── rpc/                           ← RPC & API protocols
│   ├── grpc/                      ← gRPC (HTTP/2 + protobuf)
│   └── graphql/                   ← GraphQL (HTTP + WebSocket)
├── database/                      ← Database wire protocols
│   ├── redis/                     ← Redis RESP2/RESP3
│   ├── postgresql/                ← PostgreSQL v3 frontend/backend
│   └── mysql/                     ← MySQL client/server
├── email/                         ← Email protocols
│   ├── common/                    ← Shared MIME parsing (RFC 2045-2049)
│   ├── smtp/                      ← SMTP (RFC 5321)
│   └── imap/                      ← IMAP4rev2 (RFC 9051)
├── network/                       ← Network services & management
│   ├── common/                    ← Shared BER/ASN.1 codec
│   ├── dns/                       ← DNS (RFC 1034/1035 + DNSSEC + DoH + DoT)
│   ├── ldap/                      ← LDAP v3 (RFC 4511)
│   ├── snmp/                      ← SNMP v3 (RFC 3411-3418)
│   ├── syslog/                    ← Syslog (RFC 5424)
│   ├── modbus/                    ← Modbus TCP
│   ├── ssh/                       ← SSH-2 (RFC 4251-4256, client + server, SFTP, SCP, port forwarding)
│   └── ftp/                       ← FTP/FTPS (RFC 959, RFC 4217, client + server)
└── media/                         ← Real-time media & VoIP
    ├── common/                    ← Shared SDP parser (RFC 4566)
    ├── rtsp/                      ← RTSP 2.0 (RFC 7826)
    ├── rtp/                       ← RTP/RTCP (RFC 3550)
    └── sip/                       ← SIP (RFC 3261)
```

#### Package Structure:
```
ssg.legoflow.blocks/       — core DP, DF, Context, State, Statistics
ssg.legoflow.service/      — Service, AsyncService, Scopes, Users, Manager, UDP transport
ssg.legoflow.http/         — HTTP protocol, features, security, WebSocket
ssg.legoflow.http2/        — HTTP/2 protocol, HPACK, streams, flow control, server push
ssg.legoflow.http3/        — HTTP/3 protocol, QUIC transport, QPACK compression
ssg.legoflow.ws/           — Web service components
ssg.legoflow.http.proxy/   — HTTP forward/reverse/caching proxy
ssg.legoflow.upnp/         — UPnP/DLNA: SSDP, SOAP, GENA, media services, control point
ssg.legoflow.coap/         — CoAP: constrained REST, observe, blockwise, discovery
ssg.legoflow.auth.gssapi/  — GSS-API, Kerberos V5, SPNEGO token handling (shared)
ssg.legoflow.http.auth/    — Auth framework: core, basic/digest, OAuth/OIDC, SSO/SAML
ssg.legoflow.http.auth.spnego/ — HTTP Negotiate (SPNEGO) authentication
ssg.legoflow.messaging.kafka/  — Apache Kafka wire protocol
ssg.legoflow.messaging.amqp/   — AMQP 1.0 (ISO 19464)
ssg.legoflow.messaging.stomp/  — STOMP 1.2
ssg.legoflow.messaging.nats/   — NATS core + JetStream
ssg.legoflow.mqtt/              — MQTT v3.1.1/v5.0: broker, client, pub/sub, QoS, topics
ssg.legoflow.xmpp/              — XMPP: streams, stanzas, presence, messaging, IoT
ssg.legoflow.wamp/              — WAMP core + WebSocket adapter
ssg.legoflow.rpc.grpc/         — gRPC (HTTP/2 + protobuf wire format)
ssg.legoflow.rpc.graphql/      — GraphQL query language
ssg.legoflow.database.redis/   — Redis RESP2/RESP3
ssg.legoflow.database.postgresql/ — PostgreSQL v3 wire protocol
ssg.legoflow.database.mysql/   — MySQL client/server protocol
ssg.legoflow.email.common/     — Shared MIME parsing
ssg.legoflow.email.smtp/       — SMTP (RFC 5321)
ssg.legoflow.email.imap/       — IMAP4rev2 (RFC 9051)
ssg.legoflow.network.common/   — Shared BER/ASN.1 codec
ssg.legoflow.network.dns/      — DNS (RFC 1034/1035)
ssg.legoflow.network.ldap/     — LDAP v3 (RFC 4511)
ssg.legoflow.network.snmp/     — SNMP v3 (RFC 3411-3418)
ssg.legoflow.network.syslog/   — Syslog (RFC 5424)
ssg.legoflow.network.modbus/   — Modbus TCP
ssg.legoflow.ssh/               — SSH-2: transport, kex, cipher, MAC, auth, channels, SFTP, SCP
ssg.legoflow.ftp/               — FTP/FTPS: protocol, data connections, TLS, client, server
ssg.legoflow.media.common/     — Shared SDP parser (RFC 4566)
ssg.legoflow.media.rtsp/       — RTSP 2.0 (RFC 7826)
ssg.legoflow.media.rtp/        — RTP/RTCP (RFC 3550)
ssg.legoflow.media.sip/        — SIP (RFC 3261)
```

#### Dual API Convention (service layer and above):
- **Sync procedural (primary)** — core implementation, straightforward method calls
- **Async wrapper** — `CompletableFuture<T>` return types, delegates to sync on virtual threads
- **Functional style** — lambda-friendly, composable (pipelines, builders, composers)
- Both procedural and functional coexist in same interfaces via default methods
- Pattern: `Foo` (sync) + `AsyncFoo` (wrapper) + `foo.functional/` package
- **Sync-primary is deliberate**: the NIO/selector layer is inherently async, but JDK 25 virtual threads make blocking calls near-zero-cost. Sync APIs are simpler to write, debug, and test. The async wrapper exists for callers who need `CompletableFuture` composability. See `service/doc/ARCHITECTURE.md` § "Wrapper Pattern (Async)" for full rationale.

#### Coding Conventions:
- **JDK 25 features**: virtual threads, scoped values, structured concurrency, sealed interfaces, record patterns, pattern matching for switch, stream gatherers, module imports
- **Dual API**: sync + async (CompletableFuture), procedural + functional
- **Statistics**: track all operations (count, amounts per type)
- **State management**: IDLE, CONNECTING, READY, PAUSED, FAILED, STOPPED
- **Javadoc**: all public classes and methods with @param, @return, @throws, @since
- **Thread safety**: ScopedValues for context propagation, concurrent collections, atomic counters
- **Resource management**: implement AutoCloseable, proper cleanup

### 6. Testing Practices

#### Test Categories:
1. **Unit Tests**: core components (DP, DF, Context, State, Statistics)
2. **Functional Demo Tests**: end-to-end demos exercising real usage patterns
3. **API Style Tests**: procedural sync, procedural async, functional sync, functional async

#### Test Naming:
- `test<Operation><Condition>`: e.g., `testConsumeWithFilter`
- `test<Operation>Async`: e.g., `testConsumeAsync`
- `test<Style><Feature>`: e.g., `testFunctionalPipeline`

#### Test Structure:
```java
@Test
void testFeatureDescription() {
    // Given: Setup test data

    // When: Perform operation

    // Then: Verify results using AssertJ
    assertThat(result).isNotNull();
}
```

#### Test Tools:
- **JUnit 5**: test framework
- **AssertJ**: fluent assertions
- **Mockito**: mocking

### 7. Demo Conventions

Each module has a `demo/` sub-package in both `src/main/java` and `src/test/java`:
- **Main demos**: reusable demo implementations (servers, clients, pipelines)
- **Test demos**: functional tests that exercise demos with detailed feature verification
- Demos progress from **simplest** → **average** → **complex**
- Cover both **common** and **specific** usage variants separately
- From service module upward: include procedural, functional, and combined variants

## Current Project Status

- **Total Commits**: 36
- **Total Tests**: 8582
- **Categories**: 9 (web, iot, auth, messaging, rpc, database, email, network, media)
- **Leaf Modules**: 42 (blocks, service + 40 protocol leaf modules)
- **JDK**: 25
- **Project Layout**: Maven multi-module v1.0.0-SNAPSHOT

## Notes for Future AI Sessions

1. **Always read doc/REQUIREMENTS.md first** to understand project history
2. **Check doc/ARCHITECTURE.md** for current architectural decisions
3. **Follow the established patterns** documented here
4. **Track everything** — requirements, design decisions, metrics
5. **Test comprehensively** — unit tests + demo functional tests
6. **Document thoroughly** — REQUIREMENTS.md, ARCHITECTURE.md, and README.md are mandatory deliverables
7. **Never push automatically** — inform the user and wait for explicit "push" instruction
8. **Dual API everywhere** (from service upward) — sync + async, procedural + functional
9. **JDK 25 features** — use virtual threads, scoped values, structured concurrency, sealed interfaces, pattern matching
10. **Maintain documentation navigation** — every README must link to its own Architecture/Requirements/Compliance docs; root README must have a Documentation section with tree-like references to all module/sub-module documents (3-4 links per module); update navigation when modules or documents are added/removed
11. **Use Mermaid diagrams** — all architecture diagrams use Mermaid (graph TD, flowchart LR, sequenceDiagram) instead of ASCII art

---

**Last Updated**: 2026-07-07
**For AI assistant versions**
