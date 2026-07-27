# Lego Flow Java-to-C# Conversion: Effort & Scope Estimate

## Context

**Lego Flow** is a composable data processing framework written in Java 25, implementing 30+ network protocols from scratch. The user wants a functionally equivalent C# port with equal or better test coverage, output to `/Users/sergey.sidorov/NetBeansProjects/lego-flow-csharp`.

### Source Project Metrics

| Metric | Value |
|--------|-------|
| Production code | ~197,000 lines across 1,447 files |
| Test code | ~96,000 lines across 728 files |
| **Total Java LOC** | **~293,000 lines** |
| Leaf modules | 42 across 9 categories |
| Tests | 8,136 |
| External deps (runtime) | SLF4J only (+ 3 audio codecs for media demos) |
| External deps (test) | JUnit 5, AssertJ, Mockito |
| Build system | Gradle (Kotlin DSL) + Maven |

---

## Scope of Conversion

### What Gets Converted

All 42 leaf modules + 2 foundation modules:

| Category | Modules | Est. Production LOC | Est. Test LOC |
|----------|---------|-------------------|---------------|
| **Foundation** | blocks, service | ~9,500 | ~6,100 |
| **Web** | http, http2, http3, web-services, http-proxy | ~39,000 | ~16,000 |
| **Auth** | gssapi, http-auth (core, basic-digest, oauth, sso, spnego) | ~17,500 | ~8,500 |
| **Messaging** | kafka, amqp, stomp, nats, mqtt, xmpp, wamp | ~43,000 | ~20,000 |
| **RPC** | grpc, graphql | ~6,500 | ~3,000 |
| **Database** | redis, postgresql, mysql | ~10,700 | ~5,500 |
| **Email** | smtp, imap, common | ~5,900 | ~3,000 |
| **Network** | dns, ldap, snmp, syslog, modbus, ssh, ftp, common | ~46,000 | ~22,000 |
| **IoT** | upnp, coap | ~39,000 | ~10,000 |
| **Media** | rtsp, rtp, sip, common | ~12,500 | ~6,500 |

### What Gets Excluded

- Swing/React UPnP demo applications (UI-specific, not protocol logic)
- Maven/Gradle build files (replaced by .NET solution/projects)
- Java module-info descriptors
- `.idea/` IDE configuration

---

## Java-to-C# Feature Mapping

| Java Feature | C# Equivalent | Conversion Difficulty |
|-------------|---------------|----------------------|
| Records | `record` types (C# 10+) | **Trivial** — near 1:1 |
| Sealed interfaces | Abstract classes + DUs (or sealed classes) | **Easy** — C# has no sealed interfaces but has pattern matching on types |
| Pattern matching (switch) | `switch` expressions with pattern matching (C# 8+) | **Easy** — C# pattern matching is mature |
| Generics | Generics (reified, not erased) | **Easy** — actually simpler in C# |
| Virtual threads | `Task` + `async/await` | **Medium** — different paradigm, but equivalent capability |
| Structured concurrency | `Task.WhenAll` + `CancellationToken` | **Medium** — manual but straightforward |
| Scoped values | `AsyncLocal<T>` | **Easy** — direct equivalent |
| Streams / Gatherers | LINQ | **Easy** — LINQ is more powerful |
| `CopyOnWriteArrayList` | `ImmutableList` or `lock` + `List` | **Easy** |
| `ConcurrentHashMap` | `ConcurrentDictionary` | **Trivial** |
| `AtomicReference/Long` | `Interlocked` + `volatile` | **Easy** |
| `CompletableFuture` | `Task<T>` | **Easy** — cleaner in C# |
| `ByteBuffer` | `Span<byte>`, `Memory<byte>`, `ReadOnlySequence<byte>` | **Medium** — different API, needs careful porting |
| SLF4J logging | `Microsoft.Extensions.Logging` | **Easy** |
| `@FunctionalInterface` | `Func<>`, `Action<>`, delegates | **Easy** |
| Varargs (`T...`) | `params T[]` | **Trivial** |
| Annotations | Attributes | **Easy** |

### Key Architectural Decisions for C#

1. **Target framework:** .NET 9 (latest LTS-adjacent, has all needed features)
2. **Solution structure:** One `.sln` with ~44 `.csproj` projects mirroring Java modules
3. **Concurrency model:** `async/await` + `Task` replaces virtual threads; `Channel<T>` for producer-consumer
4. **Byte handling:** `System.IO.Pipelines` + `ReadOnlySequence<byte>` for protocol codecs (superior to Java `ByteBuffer`)
5. **Test framework:** xUnit + FluentAssertions + Moq (equivalent stack)
6. **Logging:** `Microsoft.Extensions.Logging` abstractions
7. **No external protocol libraries** — same philosophy, implement from scratch

---

## Effort Estimate

### Conversion Complexity Tiers

**Tier 1 — Mechanical (70% of code):** Direct syntax translation. Records, POJOs, enums, simple classes, data models, protocol message types, codec constants, test assertions.

**Tier 2 — Moderate (20% of code):** Requires rethinking but has clear C# equivalents. Concurrency patterns (virtual threads → async/await), ByteBuffer → Pipelines, sealed interface hierarchies, service lifecycle.

**Tier 3 — Significant (10% of code):** Needs redesign. TCP/UDP server infrastructure (Java NIO → .NET Sockets/Kestrel), structured concurrency patterns, Swing demo apps (excluded), stream-oriented codec accumulator pattern.

### Module-by-Module Estimates

| Module Group | Java LOC (prod+test) | C# LOC (estimated) | Person-Days (manual) | Claude-Assisted Days |
|-------------|---------------------|--------------------|-----------------------|---------------------|
| **blocks** (foundation) | 5,600 | ~5,000 | 3 | 0.5 |
| **service** (lifecycle, scopes) | 10,000 | ~9,500 | 5 | 1 |
| **web/http** | 22,000 | ~21,000 | 10 | 2 |
| **web/http2** | 7,400 | ~7,000 | 5 | 1 |
| **web/http3** (QUIC) | 15,500 | ~15,000 | 8 | 1.5 |
| **web/web-services** | 2,500 | ~2,300 | 1.5 | 0.3 |
| **web/http-proxy** | 9,500 | ~9,000 | 5 | 1 |
| **auth/** (all 6 sub-modules) | 26,000 | ~25,000 | 12 | 2.5 |
| **messaging/kafka** | 6,500 | ~6,000 | 4 | 0.8 |
| **messaging/amqp** | 5,400 | ~5,000 | 3.5 | 0.7 |
| **messaging/stomp** | 4,500 | ~4,200 | 2.5 | 0.5 |
| **messaging/nats** | 4,000 | ~3,700 | 2.5 | 0.5 |
| **messaging/mqtt** | 13,500 | ~12,500 | 7 | 1.5 |
| **messaging/xmpp** | 14,500 | ~13,500 | 7 | 1.5 |
| **messaging/wamp** | 12,500 | ~11,500 | 6 | 1.2 |
| **rpc/grpc** | 5,000 | ~4,700 | 3 | 0.6 |
| **rpc/graphql** | 4,300 | ~4,000 | 3 | 0.6 |
| **database/redis** | 5,700 | ~5,300 | 3 | 0.6 |
| **database/postgresql** | 5,000 | ~4,700 | 3 | 0.6 |
| **database/mysql** | 4,300 | ~4,000 | 2.5 | 0.5 |
| **email/** (all 3) | 8,900 | ~8,300 | 5 | 1 |
| **network/dns** | 7,000 | ~6,500 | 4 | 0.8 |
| **network/ldap** | 10,000 | ~9,500 | 6 | 1.2 |
| **network/snmp** | 5,000 | ~4,700 | 3 | 0.6 |
| **network/syslog** | 2,800 | ~2,600 | 1.5 | 0.3 |
| **network/modbus** | 2,200 | ~2,000 | 1.5 | 0.3 |
| **network/ssh** | 20,000 | ~19,000 | 10 | 2 |
| **network/ftp** | 12,500 | ~12,000 | 6 | 1.2 |
| **network/common** (BER/ASN.1) | 2,200 | ~2,000 | 1.5 | 0.3 |
| **iot/upnp** | 40,000 | ~35,000* | 15 | 3 |
| **iot/coap** | 11,000 | ~10,500 | 5 | 1 |
| **media/** (all 4) | 19,000 | ~18,000 | 10 | 2 |

*UPnP estimate lower because Swing demo code is excluded.

### Summary Totals

| Metric | Estimate |
|--------|----------|
| **Total C# production LOC** | ~175,000 |
| **Total C# test LOC** | ~90,000 |
| **Total C# LOC** | ~265,000 |
| **.csproj projects** | ~44 |
| **Target test count** | 8,136+ |

### Effort Summary

| Approach | Duration | Notes |
|----------|----------|-------|
| **Manual (senior C# dev)** | ~160 person-days (~8 months solo) | Assumes strong protocol knowledge |
| **Claude-assisted (orchestrated)** | ~30-35 Claude-days | Parallel agent conversion, ~10 modules/day possible |
| **Hybrid (human + Claude)** | ~4-6 weeks | Human reviews/adjusts, Claude does bulk conversion |

---

## Conversion Strategy (Recommended)

### Phase 1: Foundation (Day 1-2)
1. Create .NET solution structure with all 44 projects
2. Convert `blocks` module (core DP/DF abstractions)
3. Convert `service` module (lifecycle, contexts, scopes)
4. Establish C# conventions: naming (PascalCase), async patterns, project structure

### Phase 2: Web Core (Day 3-5)
1. `web/http` — largest single module, establishes codec patterns
2. `web/http2`, `web/http3`
3. `web/web-services`, `web/http-proxy`

### Phase 3: Protocols (Day 6-20, highly parallelizable)
Convert remaining 35 modules in parallel batches of 5-8:
- Batch A: messaging (7 modules)
- Batch B: database + rpc (5 modules)
- Batch C: auth (6 sub-modules)
- Batch D: network (8 modules)
- Batch E: email + iot + media (9 modules)

### Phase 4: Integration & Testing (Day 21-25)
1. Cross-module integration testing
2. Fix any inter-module compatibility issues
3. Ensure all 8,136+ tests pass
4. Performance validation on protocol codecs

### Phase 5: Polish (Day 26-30)
1. Code review for C# idioms (are we using LINQ, async/await, Span<T> properly?)
2. XML doc comments for public APIs
3. README and solution documentation
4. NuGet package structure (if desired)

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| ByteBuffer → Pipelines mismatch | Medium | Establish accumulator pattern in `http` module first, reuse across all codecs |
| Virtual threads → async/await | Medium | Foundation module sets the pattern; all protocol modules follow |
| Sealed interface hierarchies | Low | C# discriminated unions (or abstract + derived) work well |
| Test framework differences | Low | xUnit + FluentAssertions is a near 1:1 mapping to JUnit + AssertJ |
| UPnP Swing demos | Low | Excluded from scope; protocol logic ports cleanly |
| Java NIO / Selectable channels | High | Redesign needed for .NET Socket/Pipeline model; affects service module TCP infrastructure |
| Audio codecs (MP3, FLAC) | Low | Use NAudio or similar .NET audio library |

---

## Verification Plan

1. **Per-module:** Each converted module must pass all its ported tests before moving on
2. **Cross-module:** Integration demos (e.g., HTTP client→server, MQTT broker→client) must work
3. **Test count:** Final test count must be >= 8,136
4. **Build:** `dotnet build` and `dotnet test` must succeed cleanly with zero warnings
5. **Protocol correctness:** Key protocol codecs should be tested against the Java version with identical byte sequences
