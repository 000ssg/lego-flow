# Lego Flow: Independent Effort Estimate vs. Actual Build Cost Report

## 1. Project Scope Summary (verified from code)

| Metric | Verified Value |
|--------|---------------|
| Production Java files | 1,447 |
| Test Java files | 728 |
| Total Java lines (prod + test) | ~293,000 |
| Markdown documentation files | 124 |
| Git commits | 39 |
| Leaf modules | 42 (in 9 categories) |
| Calendar duration | 20 days (2026-06-16 to 2026-07-06) |
| Tests | 8,136 (per docs) |
| External runtime deps | SLF4J only |
| JDK target | 25 (virtual threads, scoped values, sealed interfaces, etc.) |

---

## 2. Independent Effort Estimate (based on deliverables only)

This estimate is based solely on what the project contains — its architecture, code volume, protocol complexity, test coverage, and documentation — without knowledge of how it was actually built (tools, agents, parallelism, etc.).

### 2.1 Estimation Method

I use a **bottom-up module-level estimate** calibrated against typical senior Java developer productivity for protocol-level code (which is harder than CRUD/web apps due to RFC compliance, binary codecs, state machines, and extensive edge-case testing).

**Baseline productivity assumptions for a senior Java developer:**
- Simple module (data models, basic CRUD, thin wrappers): ~800-1200 LOC/day including tests
- Medium module (protocol codec, state machine, moderate RFC): ~400-600 LOC/day including tests
- Complex module (full RFC implementation, crypto, binary framing, TLS integration): ~200-400 LOC/day including tests
- Documentation (architecture docs, compliance matrices, READMEs): ~2000-3000 lines/day

### 2.2 Module-by-Module Estimates (human developer, person-days)

| Module | Total LOC (prod+test) | Complexity | Est. Person-Days |
|--------|----------------------|------------|-----------------|
| **blocks** (core DP/DF) | ~5,600 | Medium (novel abstractions) | 10 |
| **service** (lifecycle, UDP, TCP, NIO) | ~10,000 | High (NIO, virtual threads, scopes) | 25 |
| **web/http** (RFC 2616, SSL, WebSocket) | ~22,000 | High (full HTTP, many features) | 45 |
| **web/http2** (RFC 9113, HPACK) | ~7,400 | High (binary framing, compression) | 18 |
| **web/http3** (QUIC, QPACK, 0-RTT) | ~15,500 | Very High (UDP transport, loss detection, congestion) | 40 |
| **web/web-services** | ~2,500 | Low-Medium | 4 |
| **web/http-proxy** (forward/reverse/cache) | ~9,500 | Medium-High | 18 |
| **iot/upnp** (SSDP, SOAP, GENA, DLNA, demos) | ~40,000 | High (multi-protocol, XML, multicast, UI demos) | 60 |
| **iot/coap** (RFC 7252, observe, blockwise) | ~11,000 | Medium-High (UDP reliability, block transfer) | 20 |
| **auth/gssapi** | ~3,500 | High (Kerberos, SPNEGO, ASN.1) | 10 |
| **auth/http-auth** (5 sub-modules) | ~22,500 | High (OAuth, JWT from scratch, SAML) | 40 |
| **messaging/kafka** | ~6,500 | High (binary wire protocol, 60+ API keys) | 15 |
| **messaging/amqp** | ~5,400 | High (ISO 19464, type system, framing) | 12 |
| **messaging/stomp** | ~4,500 | Medium | 8 |
| **messaging/nats** | ~4,000 | Medium | 7 |
| **messaging/mqtt** | ~13,500 | Medium-High (v3.1.1+v5.0, QoS state machines) | 25 |
| **messaging/xmpp** | ~14,500 | High (XML streaming, SASL, IoT XEPs) | 28 |
| **messaging/wamp** | ~12,500 | Medium-High (advanced profile, custom serializers) | 22 |
| **rpc/grpc** | ~5,000 | High (HTTP/2 transport, protobuf wire) | 12 |
| **rpc/graphql** | ~4,300 | Medium-High (parser, validator, executor) | 10 |
| **database/redis** | ~5,700 | Medium (RESP protocol, many commands) | 10 |
| **database/postgresql** | ~5,000 | High (extended query protocol, SCRAM auth) | 12 |
| **database/mysql** | ~4,300 | Medium-High (auth plugins, result sets) | 10 |
| **email/common+smtp+imap** | ~8,900 | Medium-High (MIME, STARTTLS, IDLE) | 16 |
| **network/dns** | ~7,000 | High (all record types, DNSSEC, DoH) | 16 |
| **network/ldap** | ~10,000 | High (BER codec, filter expressions) | 20 |
| **network/snmp** | ~5,000 | High (BER, USM security, VACM) | 12 |
| **network/syslog** | ~2,800 | Low-Medium | 4 |
| **network/modbus** | ~2,200 | Low-Medium | 3 |
| **network/ssh** (transport, kex, auth, SFTP, SCP) | ~20,000 | Very High (crypto, key exchange, channels) | 45 |
| **network/ftp** | ~12,500 | Medium-High (TLS for control+data, client+server) | 20 |
| **network/common** (BER/ASN.1) | ~2,200 | High (codec foundation) | 6 |
| **media/common** (SDP) | ~1,500 | Medium | 3 |
| **media/rtsp** | ~5,500 | Medium-High | 10 |
| **media/rtp** | ~5,500 | High (jitter buffer, RTCP, SSRC) | 12 |
| **media/sip** | ~6,000 | High (dialog state machine, transactions) | 14 |

### 2.3 Non-Module Work

| Task                                                | Est. Person-Days |
|-----------------------------------------------------|-----------------|
| Project setup (root POM, structure, CI)             | 2 |
| Gradle build system (parallel to Maven)             | 2 |
| Package refactoring                                 | 1 |
| Documentation (124 markdown files, compliance matrices) | 15 |
| Cross-module integration, bug fixes                 | 10 |
| Codec audit & stream-safety fixes                   | 5 |

### 2.4 Totals

| Approach | Estimated Effort |
|----------|-----------------|
| **Single senior Java developer (manual)** | ~650-700 person-days (~2.5-3 years) |
| **Team of 3 senior devs (some parallelism)** | ~8-10 months |
| **Single dev + AI pair programming (e.g. Copilot)** | ~350-400 person-days (~1.5 years) |
| **AI-orchestrated (Claude Code, mostly autonomous)** | ~15-30 AI-days (with human review) |

---

## 3. What the Actual Build Cost Report Says

The project's `costs/BUILD_COST_REPORT.md` documents the actual build effort:

| Metric | Actual Value |
|--------|-------------|
| Calendar duration | 20 days (May 19 - Jun 8) |
| Active work sessions | Concentrated in ~6-7 active days |
| Background agent tokens (tracked) | ~879,000 |
| Estimated total tokens (incl. orchestrator) | ~2,930,000 |
| Background agent tool calls | ~1,832+ |
| Background agent wall time | ~410 min (~6.8 hours) |
| Commits | 36 (report says; git shows 39) |
| Build approach | Mostly direct orchestrator, some parallel agents |

### Key actuals from the report:
- **Agent wall time: ~410 minutes** — this is only background agents, not the orchestrating agent
- **Orchestrator did ~70% of work** — most modules were built directly, not delegated
- The report explicitly notes orchestrator token usage is **not tracked**
- Estimated ~2.93M total tokens at ~70% orchestrator share

---

## 4. Comparison: Estimated vs. Actual

### 4.1 Human Developer Estimate vs. Reality

| Dimension | My Independent Estimate | Actual (AI-built) | Ratio |
|-----------|------------------------|-------------------|-------|
| **Person-days (single human dev)** | 650-700 days | N/A (AI-built) | — |
| **Calendar time** | 2.5-3 years (1 dev) | 20 calendar days | ~50x faster |
| **Active work time** | 650-700 full days | ~6-7 concentrated days | ~100x faster |
| **AI-assisted estimate** | 15-30 AI-days | ~6-7 AI-days actual | 2-4x overestimate |

### 4.2 Where My Estimates Were Right

1. **Relative module complexity rankings** match the actual build order and effort allocation. The report shows http, ssh, upnp, and auth consumed the most effort — exactly the modules I rated "High" or "Very High."

2. **Documentation overhead** is significant. 124 markdown files, compliance matrices with per-RFC section tracking, and Mermaid diagrams represent substantial work (I estimated 15 person-days; the report shows it as a non-trivial fraction of commits).

3. **The 42-module scope is genuinely massive.** The C# conversion plan in the project also estimates ~160 manual person-days just for *porting* (not creating from scratch), which is directionally consistent with my from-scratch estimate being 4x larger.

### 4.3 Where My Estimates Were Wrong (and Why)

#### Overestimate: Human effort by ~4-5x vs. AI-orchestrated reality

**Reasons:**

1. **Protocol implementations share patterns.** Once the `service` + `http` + `blocks` foundation was built (Phase 1), every subsequent protocol module follows the same structural pattern: codec, message types, client, server, config, tests. A human developer benefits from copy-paste-modify; an AI benefits even more because it can generate the entire pattern instantaneously.

2. **RFC-compliant code is highly specifiable.** Protocol implementations are among the most AI-friendly code to generate because the RFCs provide exhaustive, unambiguous specifications. There's less creative design work and more "implement this spec." My estimates assumed human reading-and-interpreting time for RFCs, which AI eliminates.

3. **Test generation is nearly free for AI.** I estimated testing at ~40-50% of total effort (standard for protocol code). For AI, generating 8,136 tests is marginal cost once the production code exists.

4. **Documentation generation is near-zero marginal cost.** The 124 markdown files with compliance matrices would take a human weeks. For AI, generating structured documentation from the code it just wrote is trivial.

5. **Bulk refactoring operations.** The package rename (1,245 files) took the AI one agent invocation (~170K tokens, 7 min). A human would spend at least a full day, even with IDE refactoring tools.

#### Underestimate: AI-orchestrated effort (my 15-30 days vs. actual ~7 days)

**Reasons:**

1. **I underestimated parallelism.** Phase 7 added 22 modules in a single session by dispatching parallel agents. My estimate assumed more sequential work.

2. **I underestimated the leverage of foundation patterns.** Once `blocks` → `service` → `http` was established, the orchestrator could produce a new protocol module in minutes, not hours, because the pattern was fully established.

3. **UPnP demo apps inflated my estimate.** The 14 commits on UPnP demos (Swing + React, iterative debugging with real hardware) are an outlier — this is the one area where AI struggled (real-world device interop requires manual testing). But even this was compressed into ~1 day.

### 4.4 Token Cost Analysis

| Metric | Value | Notes |
|--------|-------|-------|
| Tracked agent tokens | ~879K | Background agents only |
| Estimated orchestrator tokens | ~2,050K | 70% of total (report's estimate) |
| Estimated total | ~2,930K | |
| Cost per LOC (by tokens) | ~10 tokens/LOC | 2.93M tokens / 293K LOC |
| Cost per test | ~360 tokens/test | 2.93M / 8,136 |

If we assume Opus-class pricing (~$15/M input, ~$75/M output) with a 50/50 input/output split:
- Estimated API cost: ~$130-$150 total (very rough)
- This would be the cost to produce ~293K lines of protocol-level Java with 8,136 tests

For comparison, a senior Java developer at ~$150K/year salary would cost ~$390K for the estimated 650 person-days of work.

**Cost ratio: ~2,600x cheaper via AI** (with the major caveat that AI output quality needs human review, and the token tracking is incomplete).

### 4.5 Limitations of This Comparison

1. **Orchestrator tokens are estimated, not measured.** The 70% figure is the report's own guess. Actual total could be 2-5x higher.

2. **Quality is not assessed.** This analysis compares effort, not correctness. The codec audit (Phase 9) found 9 bugs across 60+ codecs — these were bugs the AI itself introduced and then found. A human developer might have introduced fewer bugs in the first place (or more — protocol code is notoriously tricky).

3. **Human review time is not counted.** The 20 calendar days include human prompting, reviewing, and directing. That human time is not zero.

4. **The "tests pass" metric is necessary but not sufficient.** 8,136 tests passing doesn't mean the implementations are production-ready or interoperable with real-world servers/clients (the UPnP demo debugging shows this gap).

5. **Commit count discrepancy.** The report says 36 commits but git shows 39. Minor but notable — the report may have been written before the final 3 commits.

---

## 5. Key Takeaways

1. **AI-assisted development compressed ~650 human person-days into ~7 concentrated AI-days** — roughly a **100x** speedup for this type of work (protocol implementations from specs).

2. **Protocol/spec-driven code is the ideal AI use case.** Unambiguous specifications + repetitive patterns + extensive test requirements = maximum AI leverage.

3. **The foundation pattern is critical.** The first ~3 modules (blocks, service, http) took disproportionate effort to establish. Once the pattern existed, subsequent modules were generated at dramatically lower marginal cost.

4. **Documentation and testing are "free" for AI.** These traditionally consume 40-60% of human developer time but are marginal cost for AI.

5. **Real-world integration remains hard.** The UPnP demo debugging (14 commits over multiple days, interacting with real NAS devices) was the one area where AI struggled — hardware interop, XML parsing edge cases, and real network behavior can't be solved by reading RFCs alone.

6. **Quality gate exists but is deferred.** The Phase 9 codec audit found 9 bugs the AI introduced. Systematic auditing of AI-generated protocol code is essential before any production use.
