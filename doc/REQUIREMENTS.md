# Lego Flow Requirements Evolution

This document tracks all requirements, design decisions, and their evolution throughout the project development.

---

## Project Timeline Overview

- **Start Date**: June 16, 2026
- **Total Commits**: 18
- **Total Tests**: 4434 (blocks=66, service=166, http=542, web-services=69, wamp=294, http2=180, http3=313, upnp=412, mqtt=193, coap=156, xmpp=267, ssh=430, ftp=386, http-proxy=251, gssapi=105, http-auth=604 [core=132, basic-digest=87, oauth=205, sso=151, spnego=29])
- **Modules**: 18 (blocks, service, http, http2, http3, web-services, wamp, upnp, mqtt, coap, xmpp, ssh, ftp, http-proxy, auth/gssapi, auth/http-auth [core, basic-digest, oauth, sso, spnego])
- **JDK**: 25
- **Latest Feature**: Gradle build system (parallel to Maven)

---

## Table of Contents

- [Project Timeline Overview](#project-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Commit 19: CI/CD Pipeline, JMH Benchmarks, Protocol Interoperability Tests](#commit-19)
  - [Commit 18:## Commit 18 Java 24->25 upgrade, CLAUDE.md->AGENTS.md rename with symlinks](#commit-18)
  - [Commit 17: CLAUDE.md generic attribution and project maintenance](#commit-17)
  - [Commit 16: Gradle build system (parallel to Maven)](#commit-16)
  - [Commit 15: Package refactoring — com.relexsolutions → ssg](#commit-15)
  - [Commit 14: Mermaid diagrams + cross-reference document navigation](#commit-14)
  - [Commit 13: GSSAPI shared module, auth restructuring, SSH GSSAPI auth, HTTP SPNEGO auth](#commit-13)
  - [Commit 12: SSH agent forwarding, X11 forwarding, certificate host keys](#commit-12)
  - [Commit 11: Complete remaining compliance gaps](#commit-11)
  - [Commit 10: Protocol compliance completion + unified COMPLIANCE.md](#commit-10)
  - [Commit 9: Full protocol compliance + HTTP proxy + HTTP auth](#commit-9)

---

## Requirements Summary by Category

### Core Framework (blocks)
- DP<I,O> data processor with consume/produce/accept/submit methods
- DF<T> data filters with positioning rules (input boundary, output boundary)
- Context providing logging, statistics, error handling
- State management: IDLE, CONNECTING, READY, PAUSED, FAILED, STOPPED
- Statistics tracking: count/amounts per type (I, O) in/out

### Service Framework (service)
- Service abstraction extending DP with connection/transport and lifecycle
- ServiceContext with scopes: Site, Application, Session, Request
- User types: Anonymous, Shared, Exact with role-based access control
- ServicesManager with dependency-aware lifecycle management
- SelectableChannel-based default implementation with virtual threads
- UDP transport: UdpDataChannel, DatagramHandler, MulticastDataChannel, UdpChannelManager
- Multicast support: MulticastConfig, group join/leave, TTL configuration
- DatagramPacketInfo for UDP packet metadata (source/destination addresses, port)
- Dual API: sync + async, procedural + functional

### HTTP Protocol (http)
- Full RFC 2616 implementation with pluggable feature categories
- Feature system: Core, Transfer, Content, Caching, Connection, Entity, Metadata, Security, WebSocket, Static
- SSL/TLS as DataFilter, HSTS (RFC 6797)
- Standard profiles: SERVER_MINIMAL/STANDARD/FULL, CLIENT_MINIMAL/STANDARD/FULL
- Server = configurable, Client = adaptive

### Web Services (web-services)
- DP/DF components that plug into HTTP based on protocol conditions
- Content negotiation, request/response mapping
- Endpoint routing and invocation

### HTTP/2 Protocol (http2)
- Full RFC 7540 / RFC 9113 compliance — binary framing, all 10 frame types, connection preface
- HPACK header compression (RFC 7541) — static table, dynamic table, Huffman coding
- Stream multiplexing with state machine per RFC 7540 §5
- Connection-level and stream-level flow control with automatic WINDOW_UPDATE emission
- Server push via PUSH_PROMISE frames
- H2c upgrade (HTTP/1.1 → HTTP/2 cleartext)
- Http2RequestAdapter bridges to HttpRouter — existing handlers unchanged
- Http2Feature plugs into http module's feature system (HttpFeatureCategory.HTTP2)
- Virtual threads for stream processing

### HTTP/3 Protocol (http3)
- Full RFC 9114 compliance — HTTP/3 framing over QUIC transport
- QUIC transport layer (RFC 9000) — UDP-based, connection IDs, packet protection
- QPACK header compression (RFC 9204) — static table, dynamic table with encoder/decoder streams
- Stream multiplexing without head-of-line blocking (each stream independent at transport level)
- 0-RTT connection establishment for reduced latency on resumed connections
- Connection migration — seamless handover across network changes via connection IDs
- Server push via HTTP/3 PUSH_PROMISE
- Alt-Svc discovery for HTTP/3 endpoint advertisement
- Http3Feature plugs into http module's feature system (HttpFeatureCategory.HTTP3)
- Virtual threads for stream processing

### WAMP Protocol (wamp)
- Invariant base: messages, sessions, roles (Caller, Callee, Publisher, Subscriber), realms, routing
- WebSocket adapter bridging to HTTP module
- WampTransport SPI for transport abstraction

### UPnP/DLNA (upnp)
- SSDP multicast discovery (M-SEARCH, NOTIFY) over UDP 239.255.255.250:1900
- SOAP action invocation for UPnP service control
- GENA event subscription and notification (SUBSCRIBE, UNSUBSCRIBE, NOTIFY)
- ContentDirectory service: Browse, Search, GetSystemUpdateID with DIDL-Lite XML responses
- AVTransport service: Play, Pause, Stop, Seek, SetAVTransportURI with state machine (STOPPED, PLAYING, PAUSED, TRANSITIONING)
- RenderingControl service: GetVolume, SetVolume, GetMute, SetMute
- Media server: ContentDirectory + ConnectionManager, DLNA profiles, protocolInfo
- Media renderer: AVTransport + RenderingControl, playback state management
- Control point: device discovery, description fetch, action invocation, event subscription lifecycle
- DIDL-Lite XML parsing and generation for media metadata
- DLNA profile support and protocol info negotiation
- Dual API: sync + async, procedural + functional

### MQTT Protocol (mqtt)
- MQTT v3.1.1 (OASIS) and v5.0 protocol support
- Publish/subscribe messaging with topic-based routing
- QoS levels 0 (at most once), 1 (at least once), 2 (exactly once)
- Topic wildcards: single-level (+) and multi-level (#)
- Retained messages and last will / testament
- Broker implementation with session persistence, topic tree, subscription management
- Client implementation with auto-reconnect, keep-alive, message queuing
- Packet codec for all 15 MQTT control packet types
- Dual API: sync + async, procedural + functional

### CoAP Protocol (coap)
- CoAP (RFC 7252) constrained RESTful protocol over UDP
- Confirmable (CON), Non-confirmable (NON), Acknowledgement (ACK), Reset (RST) message types
- GET, PUT, POST, DELETE methods with option-based request/response model
- Observe pattern (RFC 7641) for resource change notifications
- Blockwise transfer (RFC 7959) for large payloads (Block1/Block2)
- Resource discovery via /.well-known/core (RFC 6690) with CoRE Link Format
- Content-Format negotiation (text/plain, application/json, application/cbor)
- Deduplication, retransmission, and congestion control
- Dual API: sync + async, procedural + functional

### XMPP Protocol (xmpp)
- XMPP core (RFC 6120): XML stream establishment, SASL authentication, TLS negotiation, stanza routing
- XMPP IM (RFC 6121): presence management, messaging, roster (contact list) with subscription states
- IoT extensions: sensor data (XEP-0323), control (XEP-0325), discovery/provisioning (XEP-0347)
- Stanza types: message, presence, iq (info/query) with error handling
- Multi-user chat (XEP-0045) and publish-subscribe (XEP-0060) support
- Stream management (XEP-0198) for reliable delivery and session resumption
- Dual API: sync + async, procedural + functional

### Authentication (auth)
- Shared GSSAPI module: GSS-API context management, Kerberos V5, SPNEGO token handling, credential management
- HTTP auth framework: AuthenticationScheme SPI, sessions, JWT (HS256/RS256 from scratch)
- Basic/Digest auth (RFC 7617/7616), OAuth 2.0/OIDC with 7 provider templates, SSO/SAML
- HTTP SPNEGO (RFC 4559) via shared GSSAPI module
- SSH GSSAPI auth (RFC 4462 "gssapi-with-mic") via shared GSSAPI module

---

## Commit 12: SSH Agent Forwarding, X11 Forwarding, Certificate Host Keys (2026-06-26) {#commit-12}

### Original Request
> "add in ssh module support for current limitations 2,3, and 5"

### Reformulated Requirements
1. SSH agent forwarding — agent protocol messages, in-memory agent, forwarding channel, session request
2. X11 forwarding — X11 channel type, forwarding config with MIT-MAGIC-COOKIE-1, session request
3. Certificate-based host key authentication — OpenSSH certificate parsing/encoding/validation, certificate host key algorithm wrapping standard algorithms, CA-signed certificate issuance

### Final Design Decisions
- Agent uses sealed interface `SshAgentMessage` with 9 message types matching draft-miller-ssh-agent
- Agent forwarding channel type `"auth-agent@openssh.com"` auto-created by `SshConnection` on CHANNEL_OPEN
- X11 config uses `generate()` factory with random 16-byte MIT-MAGIC-COOKIE-1
- Certificate algorithm wraps underlying HostKeyAlgorithm + CA key pair for issuance and verification
- 3 cert variants registered in HostKeyFactory: ed25519, ecdsa-nistp256, rsa-sha2-256

### Implementation Details
- Agent: SshAgentMessage (sealed, 9 types), SshAgentCodec, SshAgent (ConcurrentHashMap), AgentForwardingChannel
- X11: X11ForwardingConfig (record), X11ForwardingChannel, SessionChannel.requestX11Forwarding()
- Certificates: CertType enum, SshCertificate (parse/encode/isValid), CertificateHostKeyAlgorithm (issue/verify)
- SshConnection: handles CHANNEL_OPEN for agent and x11 types
- HostKeyFactory: 3 new cert algorithm entries + createCertificate() factory method

### Test Coverage
- New tests added: 82 (agent=34, x11=17, certificates=31)
- SSH total: 416 (was 334)
- Total project: 4286 across 16 modules

---

<a id="commit-18"></a>
## Commit 18: Java 24->25 upgrade, CLAUDE.md->AGENTS.md rename with symlinks (2026-07-26)

### Original Request
> "Switch all projects to Java 25, rename CLAUDE.md files to AGENTS.md and create symlinks"

### Reformulated Requirements
1. Upgrade Java version from 24 to 25 in pom.xml and build.gradle.kts
2. Rename all CLAUDE.md files across root and nested modules to AGENTS.md
3. Create backward-compatible CLAUDE.md -> AGENTS.md symlinks

### Final Design Decisions
- **AGENTS.md as canonical filename**: Renamed to support general agentic development; CLAUDE.md remains as symlink for Claude Code compatibility
- **Java 25 everywhere**: Maven pom.xml (maven.compiler.release) and Gradle (JavaLanguageVersion, options.release, javaRelease property)

### Implementation Details
- Files modified: pom.xml, build.gradle.kts, gradle.properties, all AGENTS.md files (47+), symlinks created

### Test Coverage
- No new tests added; existing 4434 tests unaffected

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 (direct edit) |
| Agent tokens | N/A |
| Agent tool calls | N/A |
| Agent wall time | ~10 min |
| Files created/modified | 47+ |
| Lines added/removed | +50 / -50 |
| Tests added | 0 (total: 4434) |

<a id="commit-17"></a>
## Commit 17: CLAUDE.md generic attribution and project maintenance (2026-07-26)

### Original Request
> "Fix CLAUDE.md to replace any specific co-author (e.g. with 'Opus 4.6' and Claude Code mentions) with 'AI assistant' to exclude ambiguity."

### Reformulated Requirements
1. Replace all references to Claude model names (Opus 4.6, Sonnet 4.6) in CLAUDE.md files with generic "AI assistant"
2. Replace Co-Authored-By lines referencing specific models and anthropic.com email
3. Clean up stale .claude worktree git entries
4. Update build configuration: Gradle wrapper upgrade, property-based Java version

### Final Design Decisions
- **Generic attribution**: Replaced all specific AI model names with "AI assistant" to avoid ambiguity since projects were developed using different models over time
- **.claude/ exclusion**: Added .claude/ worktree directories to .gitignore and removed stale git references
- **Gradle 9.6.1**: Upgraded wrapper from 9.5.1 to 9.6.1
- **Property-based toolchain**: Changed Java version from hardcoded `25` to `javaRelease` property

### Implementation Details
- Files modified: 47 CLAUDE.md files across root and all nested modules, .gitignore, build.gradle.kts, gradle/wrapper/gradle-wrapper.properties, iot/upnp/src/main/java/ssg/legoflow/upnp/demo/mccweb/MccWebServer.java
- Deleted 9 stale .claude/worktrees entries
- 56 files changed, 108 insertions(+), 98 deletions(-)

### Test Coverage
- No new tests added; existing 4434 tests unaffected

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 (direct edit) |
| Agent tokens | N/A |
| Agent tool calls | N/A |
| Agent wall time | ~10 min |
| Files created/modified | 47+ |
| Lines added/removed | +108 / -98 |
| Tests added | 0 (total: 4434) |

## Commit 16: Gradle Build System — Parallel to Maven (2026-06-26) {#commit-16}

### Original Request
> "add in parallel to maven gradle project for the whole lego-flow modules hierarchy. For gradle introduce by default parallel execution (up to 10 processes) for building/testing."

### Reformulated Requirements
1. Add Gradle Kotlin DSL build files for all 20 modules (matching Maven module hierarchy)
2. Configure parallel execution by default with up to 10 workers
3. Mirror all Maven dependency configurations in Gradle
4. Include Gradle wrapper for reproducible builds
5. Maven build must remain fully functional (dual build system)

### Final Design Decisions
- Gradle Kotlin DSL (`build.gradle.kts`) for type-safe build scripts
- Gradle 9.5.1 (latest, supports JDK 25)
- `gradle.properties` centralizes all version constants and parallel config
- Root `build.gradle.kts` applies `java-library` plugin to all leaf subprojects, skipping parent-only projects (`auth`, `http-auth`)
- Module names use Gradle's natural hierarchical paths (`:blocks`, `:auth:gssapi`, `:auth:http-auth:core`)
- Parallel execution: `org.gradle.parallel=true`, `org.gradle.workers.max=10`
- Per-module test parallelism: `maxParallelForks=4` (mirrors Maven surefire config)
- Build caching enabled: `org.gradle.caching=true`

### Implementation Details
- Created 23 files: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`, 20 module `build.gradle.kts` files
- Generated Gradle wrapper (gradlew, gradlew.bat, gradle/wrapper/)
- Updated `.gitignore` with Gradle entries (`.gradle/`, `build/`)
- Updated `README.md` with Gradle build instructions
- Both Maven (`mvn test`) and Gradle (`./gradlew build`) produce identical results

### Test Coverage
- No test changes (build-system-only commit)
- All 4434 tests pass on both Maven and Gradle
- Gradle clean build: ~11s (parallel) vs Maven: ~50s (sequential)

---

## Commit 15: Package Refactoring — com.relexsolutions → ssg (2026-06-26) {#commit-15}

### Original Request
> "refactor all java code to replace 'com.relexsolutions' with 'ssg' packaging."

### Reformulated Requirements
1. Replace all `com.relexsolutions` package declarations and imports in Java files with `ssg`
2. Move all source directories from `com/relexsolutions/` to `ssg/` hierarchy
3. Update all POM files (groupId, package references) to use `ssg`
4. Update all documentation (.md files) referencing the old package names
5. Ensure all 4434 tests continue to pass after the refactoring

### Final Design Decisions
- Simple global find-and-replace: `com.relexsolutions` → `ssg` in all text content
- Directory move: `src/*/java/com/relexsolutions/legoflow/` → `src/*/java/ssg/legoflow/`
- Used `git mv` for proper rename tracking in version history
- groupId in all POMs changed from `com.relexsolutions` to `ssg`

### Implementation Details
- 1200 Java files moved via `git mv` (directory restructure)
- 1241 files with text content changes (package declarations, imports, groupId, doc references)
- 23 POM files updated (groupId)
- 17 Markdown files updated (package references in documentation)
- All 40 source directory trees (20 main + 20 test) relocated
- Cleaned up empty `com/relexsolutions/` directories and stale `.DS_Store` files

### Test Coverage
- No test changes (refactoring-only commit)
- Total project: 4434 across 18 modules — all passing after refactoring

---

## Commit 14: Mermaid Diagrams + Cross-Reference Document Navigation (2026-06-26) {#commit-14}

### Original Request
> "use in documentation charts/diagrams rather than ascii graphics. Add in root module documents references to same kind of documents in modules hierarchy via tree-like menu. also add in readme documents references to architecture/requirements and, if applicable, compliance documents of the module and tree-like references to sub-module documents if applicable. in main readme add section with tree-like references to all sub-module documents as 3 or 4 links per module/submodule. this should simplify documents navigation and also these changes should be maintained whenever projects structure changes and/or documents are added/removed."

### Reformulated Requirements
1. Replace all ASCII art diagrams with Mermaid diagrams across all documentation files
2. Add cross-reference Documentation sections in every module README linking to own Architecture/Requirements/Compliance docs
3. Add Documentation section in root README with tree-like navigation to all 18 modules (3-4 links per module)
4. Add Related Documentation footers in all ARCHITECTURE.md files
5. Add Module Documentation references section at end of root doc/ARCHITECTURE.md
6. Establish convention: navigation links must be maintained when project structure or documents change

### Final Design Decisions
- Mermaid diagram types used: `graph TD` for layered architecture, `flowchart LR` for dependency graphs, `graph TD` with subgraphs for protocol layers, `graph LR` for data flows, `sequenceDiagram` where applicable
- Documentation sections use consistent format: links to own docs first, then sub-module tree (if parent), then root docs link
- Auth sub-modules use proper relative paths through the hierarchy (e.g., `../../../doc/ARCHITECTURE.md`)
- ARCHITECTURE.md files get "Related Documentation" footer with links back to README and sibling docs
- Root README Documentation section organized alphabetically with auth hierarchy properly nested

### Implementation Details
- **44 files modified** across the entire project
- Root README.md: 2 ASCII diagrams → Mermaid (`graph TD` with subgraphs, `flowchart LR`); new Documentation section with all 18 modules
- Root doc/ARCHITECTURE.md: 2 ASCII diagrams → Mermaid; Module Documentation references added
- 20 module READMEs: Documentation sections with cross-references added
- 21 module ARCHITECTURE.md files: ~57 Mermaid diagrams total + Related Documentation footers
- CLAUDE.md: added conventions #10 (maintain documentation navigation) and #11 (use Mermaid diagrams)

### Test Coverage
- No test changes (documentation-only commit)
- Total project: 4434 across 18 modules (unchanged)

---

## Commit 13: GSSAPI Shared Module, Auth Restructuring, SSH GSSAPI Auth, HTTP SPNEGO Auth (2026-06-26) {#commit-13}

### Original Request
> "check if gssapi (with variants) can be implemented in separate module to allow re-use or if it is ssh specific and there's no benefit of enabling its re-use. if separate module is worth, then refactor current modules: create auth module, move httpAuth as its sub-module, make gssapi as auth submodule, and implement variants there. use implementation for ssh to complete limitation for missing gssapi support. if no need, just add support for gssapi in the ssh module."

### Reformulated Requirements
1. Evaluate GSSAPI reusability across protocols (SSH, HTTP SPNEGO, XMPP SASL, FTP)
2. If reusable: create `auth/` parent module, move `http-auth/` under it, create `auth/gssapi/` shared module
3. Implement GSS-API primitives: context factory, context wrapper, OID constants, SPNEGO token handler, Kerberos credential manager
4. Create `auth/http-auth/spnego/` sub-module bridging GSSAPI to HTTP Negotiate (RFC 4559)
5. Add GSSAPI authentication to SSH module (RFC 4462 "gssapi-with-mic")
6. Remove "No GSSAPI authentication" from SSH Known Limitations
7. Ensure all COMPLIANCE.md files follow the unified HTTP module style
8. All new modules must have complete documentation: CLAUDE.md, README.md, REQUIREMENTS.md, ARCHITECTURE.md, COMPLIANCE.md

### Final Design Decisions
- GSSAPI is reusable across SSH (RFC 4462), HTTP (RFC 4559 SPNEGO), XMPP (SASL), FTP (RFC 2228) — shared module justified
- Auth module hierarchy: `auth/` parent POM → `auth/gssapi/` + `auth/http-auth/` (moved from root)
- All crypto delegates to JDK's `org.ietf.jgss` and `javax.security.auth` — no third-party dependencies
- SPNEGO token handling (ASN.1 DER) implemented from scratch in `SpnegoTokenHandler`
- SSH GSSAPI auth is multi-round-trip: USERAUTH_REQUEST → GSSAPI_TOKEN exchange → GSSAPI_MIC
- HTTP SPNEGO uses `NegotiateAuthScheme` implementing the existing `AuthenticationScheme` SPI

### Implementation Details
- Created `auth/pom.xml` parent POM (packaging=pom, modules: gssapi, http-auth)
- Moved `http-auth/` → `auth/http-auth/` via `git mv`, updated parent references
- Created `auth/gssapi/`: GssOids, GssConfig (builder), GssException, GssContextFactory, GssContextWrapper, SpnegoTokenHandler, KerberosCredentialManager — 105 tests
- Created `auth/http-auth/spnego/`: NegotiateAuthScheme, SpnegoConfig (builder) — 29 tests
- Added SSH `GssApiAuth` (implements AuthMethod, "gssapi-with-mic") — 14 new tests (SSH total: 430)
- Updated root POM: module `http-auth` → `auth`, added gssapi/spnego to dependencyManagement
- Fixed all COMPLIANCE.md files to use unified style (6 files updated)
- Full documentation for gssapi module (CLAUDE.md, README.md, REQUIREMENTS.md, ARCHITECTURE.md, COMPLIANCE.md)
- Full documentation for spnego module (CLAUDE.md, README.md, REQUIREMENTS.md, ARCHITECTURE.md, COMPLIANCE.md)
- Updated SSH docs (CLAUDE.md, README.md, COMPLIANCE.md) for GSSAPI addition

### Test Coverage
- New tests added: 148 (gssapi=105, spnego=29, SSH GSSAPI=14)
- SSH total: 430 (was 416)
- Total project: 4434 across 18 modules (was 4286 across 16)

---

## Commit 11: Complete Remaining Compliance Gaps (2026-06-26) {#commit-11}

### Original Request
> "complete the rest non-compliances (at least in ftp and ssh modules, may be in others also)"

### Reformulated Requirements
1. FTP: implement ACCT, SMNT commands; expand OPTS to support MLST option
2. SSH: implement SFTP EXTENDED/EXTENDED_REPLY (posix-rename, statvfs); SCP timestamp preservation (-p flag)
3. HTTP Auth Digest: MD5-sess algorithm, proxy authentication (407), Authentication-Info header
4. HTTP Auth OAuth: JWK Set fetching/key rotation, token introspection (RFC 7662), implicit/hybrid flows, dynamic client registration (RFC 7591)
5. HTTP Auth SSO/SAML: AuthnRequest generation, POST binding form, XML Signature validation, SAML Logout, encrypted assertion decryption

### Final Design Decisions
- FTP OPTS uses registry pattern (Map<String, Function>) for extensible option handling
- SFTP posix-rename uses Files.move with ATOMIC_MOVE + REPLACE_EXISTING for correctness
- SAML artifact binding and full SLO intentionally deferred — too complex for the value
- DigestAuthScheme gains proxyMode flag for 407 vs 401 responses
- JWK Set parsing extracts RSA keys (kty=RSA, n, e) with kid-based lookup and cache invalidation

### Implementation Details
- FTP: +19 tests → 386 — ACCT stores account on session, SMNT accepts root, OPTS registry with UTF8+MLST
- SSH: +15 tests → 334 — SFTP Extended (posix-rename, statvfs, unknown→OP_UNSUPPORTED), SCP T commands for mtime/atime
- HTTP Auth Basic/Digest: +14 tests → 87 — MD5-sess/SHA-256-sess variants, proxy 407, Authentication-Info rspauth
- HTTP Auth OAuth: +30 tests → 205 — JwkSet/JwkSetFetcher, introspect endpoint, implicit+hybrid flows, dynamic registration
- HTTP Auth SSO: +48 tests → 151 — SamlAuthnRequest, SamlPostBinding, SamlSignatureValidator, SamlLogout, SamlEncryptedAssertion

### Test Coverage
- New tests added: 126 (FTP +19, SSH +15, auth/basic-digest +14, auth/oauth +30, auth/sso +48)
- Total tests: 4204 across 16 modules
- Remaining intentional gaps: CoAP proxy, SAML artifact binding, SAML full SLO

---

## Commit 10: Protocol Compliance Completion + Unified COMPLIANCE.md (2026-06-26) {#commit-10}

### Original Request
> "unify presentation of compliance files to use same design as e.g. for http (with visual-friendly markers), as for new modules (like ftp and ssh) these documents are blind. Just use same visually representative style. fix the 1 flaky test. complete implementation of other modules. since in many of those TLS-related functionality is not completed, consider, if it requires shared implementation (over re-using build-in java support for TLS) and if there're other shareable utilities, and move them into own utils module or, if not relevant, just do per-module implementations. as result all protocol implementations should have no gaps unless too much efforts to reach full compliance."

### Reformulated Requirements
1. Unify all COMPLIANCE.md files to use ✅/⚠️/❌ markers in 4-column tables (Section, Requirement, Status, Verification)
2. Fix flaky CoAP DiscoveryDemoTest (port conflict + timing under load)
3. Complete XMPP gaps: MUC (XEP-0045), PubSub (XEP-0060), Stream Management (XEP-0198), STARTTLS, JID normalization
4. Complete MQTT gaps: TLS support, session expiry, clean start, QoS downgrade, keep-alive enforcement, authenticator SPI, disconnect reason codes
5. Complete CoAP gaps: separate (delayed) response, multicast support
6. Complete FTP gaps: REST restart offset, CCC command, implicit FTPS
7. Complete HTTP/2 gaps: priority-based scheduling, HPACK sensitive header marking (never-indexed)
8. Complete HTTP/3 gaps: loss detection (RFC 9002), congestion control (Reno AIMD), TLS 1.3 engine for QUIC
9. TLS decision: per-module using JDK's javax.net.ssl.SSLEngine (no shared utils module — each protocol wraps TLS differently)

### Final Design Decisions
- TLS per-module: MQTT wraps whole connection, XMPP uses STARTTLS mid-stream, FTP wraps control+data channels separately, HTTP/3 wraps QUIC CRYPTO frames — all use javax.net.ssl.SSLEngine but integration differs enough that sharing would add complexity
- COMPLIANCE.md unified format: 4-column tables with ✅ Implemented / ⚠️ Partial / ❌ Missing markers, Verification column references test class names
- CoAP proxy support intentionally skipped (too much effort for constrained protocol module)
- FTP STRU (File only) and MODE (Stream only) documented as intentional limitations

### Implementation Details
- XMPP: +105 tests — MUC (MucOccupant, MucMessage, MucRoom, MucRoomManager), PubSub (PubSubNode, PubSubItem, PubSubSubscription, PubSubManager), StreamManagement, TlsHandler, JID NFKC normalization
- MQTT: +47 tests — MqttTlsConfig, MqttAuthenticator, InMemoryAuthenticator, session expiry sweep, keep-alive timeout (1.5x interval), QoS downgrade, DISCONNECT reason codes, will message handling
- CoAP: +5 tests — CoapExchange.respondSeparate() (empty ACK + deferred CON), CoapServer.joinMulticastGroup() with NON enforcement
- FTP: +9 tests — REST offset (single-use semantics), CCC command (control TLS disable), implicit FTPS (immediate handshake)
- HTTP/2: +12 tests — priority scheduling (dependency/weight/exclusive reparenting, bandwidth allocation), HPACK never-indexed encoding (0x10 prefix, sensitive header detection)
- HTTP/3: +43 tests — QuicLossDetection (RFC 9002 §5-6, packet/time threshold, PTO), QuicCongestionController (Reno AIMD, slow start, recovery, persistent congestion), QuicTlsEngine (TLS 1.3 wrapper, ALPN h3)
- SSH/FTP COMPLIANCE.md: converted from plain text to ✅/⚠️/❌ 4-column format
- CoAP flaky test: fixed DiscoveryDemoTest with ephemeral port + settling delay + timeout guards

### Test Coverage
- New tests added: 221 (XMPP +105, MQTT +47, HTTP/3 +43, HTTP/2 +12, FTP +9, CoAP +5)
- Total tests: 4078 across 16 modules
- All COMPLIANCE.md files now use unified visual format with ✅ markers

---

## Commit 9: Full Protocol Compliance + HTTP Proxy + HTTP Auth (2026-06-26) {#commit-9}

### Original Request
> "finalize http compliance (add all missing and complete partial specifications with proper tests). add httpProxy module to encapsulate related functionality and allow both server and client functionality re-usable/pluggable. add httpAuth module with http authorization variants including OAuth variants for popular authentication providers (like social network and others), SSO, reverse proxy SSO - as sub-modules of auth to allow selected choice. add binary serialization and implement advanced profile fully. always update COMPLIANCE.md when changes affect it. complete upnp module to reach full compliance."

### Reformulated Requirements
1. Complete HTTP/1.1 compliance — implement all ❌ and ⚠️ items across RFC 7230-7235, RFC 6455
2. Complete UPnP compliance — embedded devices, ContentDirectory Search, full AV services, DLNA headers, multi-interface SSDP
3. Complete WAMP Advanced Profile — MessagePack/CBOR serialization from scratch, pattern subscriptions, progressive results, call cancellation, shared registrations, auth (CRA/ticket/cryptosign), authorization, session meta API
4. Create http-proxy module — forward proxy (CONNECT, Via/XFF), reverse proxy (load balancing, health checks), caching proxy
5. Create http-auth module — 4 sub-modules: core (auth framework, sessions, JWT), basic-digest (RFC 7617/7616), oauth (OAuth 2.0, PKCE, OIDC, provider templates), sso (reverse proxy SSO, SAML)

### Final Design Decisions
- HTTP compliance: 14 new handler classes covering close-delimited bodies, If-Range, multipart ranges, Expires, Authorization, WebSocket close/subprotocol/extensions, CONNECT, OPTIONS/TRACE, redirects, Date, 100-continue, pipelining
- UPnP compliance: SearchCriteria parser, multi-channel RenderingControl (7 channels), brightness/contrast/color, DLNA HTTP headers, embedded devices, multi-interface SSDP
- WAMP: MessagePack and CBOR serializers built from scratch (no libraries); all Advanced Profile features implemented
- http-proxy: 3 proxy types (forward, reverse, caching) with pluggable access control, load balancing (round-robin, least-connections), health checking
- http-auth: modular sub-module design allowing selective dependency; JWT from scratch (HS256/HS384/HS512/RS256), OAuth2 server+client, 7 provider templates (Google, GitHub, Microsoft, Facebook, Twitter, Apple, generic), SAML assertion parsing

### Implementation Details
- HTTP: 542 tests (was 378), 14 new source files, 9 new header constants
- UPnP: 412 tests (was 262), SearchCriteria parser, 150 new tests across 11 test files
- WAMP: 294 tests (was 113), 13 new serialization/auth files, 181 new tests
- http-proxy: 251 tests, 28 source files, 20 test files — forward/reverse/caching proxy
- http-auth: 483 tests across 4 sub-modules (core=132, basic-digest=73, oauth=175, sso=103), 58 source files, 45 test files

### Test Coverage
- New tests added: 1229 (HTTP +164, UPnP +150, WAMP +181, http-proxy +251, http-auth +483)
- Total tests: 3857 across 16 modules
- All COMPLIANCE.md files updated to reflect ✅ status

---

## Commit: `(pending)` — Protocol Add-on Modules & Module Reorganization (2026-07-04)

### Original Request
> "Add 19 new application-level protocol modules as submodules under 6 category parent modules (messaging, rpc, database, email, network, media). Full compatibility preferred over partial implementations."
> "Move old protocol modules to proper category sub-modules or add new category module if does not fit."

### Reformulated Requirements
1. Implement 22 new leaf modules (19 protocols + 3 shared common modules) under 6 category parents
2. Fix compilation and test failures in all new modules (12 fixes applied)
3. Implement 4 missing modules (LDAP, SNMP, RTP, SIP) that were incomplete from agent failures
4. Reorganize existing protocol modules into proper categories:
   - web/ — http, http2, http3, web-services, http-proxy
   - iot/ — upnp, coap
   - messaging/ — add mqtt, xmpp, wamp to existing kafka, amqp, stomp, nats
   - network/ — add ssh, ftp to existing dns, ldap, snmp, syslog, modbus
5. Add per-commit cost tracking to REQUIREMENTS.md entries
6. Update all documentation to reflect new module structure

### Final Design Decisions
- 9 category parent modules: web, iot, auth, messaging, rpc, database, email, network, media
- Shared common modules: email/common (MIME), network/common (BER/ASN.1), media/common (SDP)
- Package names unchanged — only directory locations and Maven parent references moved
- WAMP placed in messaging/ (pub/sub + RPC messaging protocol)
- SSH and FTP placed in network/ (network service protocols)
- UPnP and CoAP placed in iot/ (IoT/discovery protocols)

### Implementation Details
- 22 new protocol leaf modules implemented across 6 categories
- 12 existing modules moved into category sub-directories
- 2 new category parent POMs created (web/, iot/)
- 2 existing category parent POMs updated (messaging/, network/)
- Root POM simplified to 11 modules (2 core + 9 categories)
- 12 compilation/test fixes applied to new modules
- 4 modules implemented from scratch by background agents (LDAP, SNMP, RTP, SIP)
- JitterBuffer.poll() fix: removed incorrect isLate condition for gap handling

### Test Coverage
- New tests added: ~3,700 (across 22 new modules)
- Total tests: 8,136 across 42 leaf modules
- All tests pass: BUILD SUCCESS

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | ~20 (4 module implementations, 4 module moves, 12+ compilation/research agents) |
| Agent tokens | ~400,000 |
| Agent tool calls | ~800 |
| Agent wall time | ~60 min |
| Files created/modified | ~650 |
| Lines added/removed | +85,000 / -0 (new modules), +200 / -200 (reorganization) |
| Tests added | ~3,700 (total: 8,136) |

---

## Document Maintenance

- This document is append-only for commit sections
- Table of Contents updated with each new commit
- Project Timeline Overview updated with current statistics

## Commit: `(pending)` — Fix CI Interoperability Tests Pipeline Failure (2026-07-30)

### Original Request
> "git pipelines failed for commits. pipeline log"
> "still failed pipeline" 
> "errors running interoperability tests now"

### Reformulated Requirements
1. Diagnose root cause of CI interoperability tests failure
2. Fix the Maven command in GitHub Actions workflow to work correctly
3. Ensure all 21 interoperability tests pass in CI environment

### Root Cause Analysis
The `interoperability-tests` job in `.github/workflows/ci.yml` used YAML implicit folding (no `|` block scalar indicator) for a multi-line Maven command with backslash line continuations:

```yaml
run: mvn -B verify -pl interop-tests -am \
  -P all \
  -DskipInteropTests=false \
  ...
```

In YAML implicit folding, newlines followed by indentation are converted to spaces but backslashes remain as literal characters. When GitHub Actions passes this folded string to `bash -e {0}`, bash word-splitting (shlex) treats `\` + space as "escaped-space", leaving a **leading space** on the next token:

- Maven received `" -P"` instead of `-P` → interpreted as unknown lifecycle phase
- All 13 `-D` properties had leading spaces → silently ignored by Maven
- Result: `Unknown lifecycle phase " -P"` error, BUILD FAILURE

The `build` job's coverage step worked correctly because it used `|` block scalar which preserves actual newlines, allowing bash line continuation (`\` + newline) to function properly.

### Final Design Decisions
- Consolidate the entire Maven command onto a single line without backslash continuations
- This avoids YAML implicit folding issues entirely — no backslashes means no escaped-space problem
- Verified locally: all 21 interoperability tests pass with the single-line command (both Maven and Gradle)

### Implementation Details
- Modified `.github/workflows/ci.yml`: replaced 15-line multi-line Maven command with single-line equivalent
- No changes to test code, properties, or test execution logic
- All system properties passed identically, just without line breaks

### Test Coverage
- Verified locally: `mvn verify -pl interop-tests -am -P all -DskipInteropTests=false ...` → 21/21 passing
- Verified Gradle parity: `./gradlew :interop-tests:test -DskipInteropTests=false --rerun-tasks` → BUILD SUCCESSFUL
- Docker services (nginx, mosquitto, redis, postgresql) confirmed healthy during local testing

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~5,000 |
| Agent tool calls | ~30 |
| Agent wall time | ~10 min |
| Files created/modified | 2 (ci.yml, REQUIREMENTS.md) |
| Lines added/removed | +1 / -14 |
| Tests added | 0 (all existing tests verified passing) |

