# Lego Flow Development Guide

This document describes the development practices, patterns, and conventions used in the Lego Flow project.

## Quick Reference — Sub-Documentation

| Topic | Document | When to Load |
|-------|----------|-------------|
| Test patterns & anti-patterns | [doc/AGENTS_test_patterns.md](doc/AGENTS_test_patterns.md) | Writing/fixing tests, CI failures |
| Protocol accuracy rules | [doc/AGENTS_protocol_accuracy.md](doc/AGENTS_protocol_accuracy.md) | Protocol tests, wire format, interop debugging |
| AMQP module notes | [messaging/amqp/AGENTS.md](messaging/amqp/AGENTS.md) | AMQP-specific work |
| Protocol client implementation | [messaging/amqp/AGENTS.md](messaging/amqp/AGENTS.md) | New protocol module |
| Service patterns | [service/AGENTS.md](service/AGENTS.md) | Service layer work |

---

## Project Overview

**Lego Flow** is a composable data processing framework for Java built on JDK 25. It provides layered abstractions for building data-driven services: composable blocks (DP/DF), service orchestration, HTTP protocol, web services, and messaging protocol support.

- **Total Tests**: 8582
- **Categories**: 9 (web, iot, auth, messaging, rpc, database, email, network, media)
- **Leaf Modules**: 42 (blocks, service + 40 protocol leaf modules)
- **JDK**: 25

---

### ⚠️ CRITICAL: YAML Multi-Line Commands in GitHub Actions

**NEVER use backslash line continuations (`\`) in `run:` blocks.** Always write multi-line shell commands on a single line.

GitHub Actions uses YAML implicit folding. Folding converts newlines to spaces but **keeps backslashes literally**:

```yaml
# ❌ BROKEN — becomes: mvn -B verify " --no-daemon" ...
run: mvn -B verify \
  --no-daemon \
  -DskipInteropTests=false

# ✅ CORRECT — single line:
run: mvn -B verify --no-daemon -DskipInteropTests=false ...
```

---

## Development Practices

### 1. Requirements Documentation

All requirements, design decisions, and their evolution MUST be tracked in `doc/REQUIREMENTS.md` (append-only, commit-based).

### 2. Architecture Documentation

`doc/ARCHITECTURE.md` documents the **current** set of architectural decisions. Updated in place on every commit with architectural changes.

### 3. Git Commit Practices

- Stage changes: `git add <files>`
- Commit with detailed message using heredoc
- Always include `Co-Authored-By: AI assistant`
- **Update doc/REQUIREMENTS.md** with commit documentation
- **Update doc/ARCHITECTURE.md** if architectural changes were made
- **Update README.md**: reflect API changes, new features, version badges
- **NEVER run `git push` automatically** — inform the user and wait
- **NEVER change git remote, user.name, or user.email** — EVER. The only exception is temporarily switching to HTTPS for read access, then immediately restoring the original.

### 4. Documentation Rules

- **README.md, ARCHITECTURE.md, COMPLIANCE.md** describe **current state only** — no historical/versioning references
- Use Mermaid diagrams, not ASCII art
- Every README must link to its own Architecture/Requirements/Compliance docs

---

## Coding Conventions

- **JDK 25 features**: virtual threads, scoped values, structured concurrency, sealed interfaces, record patterns, pattern matching for switch
- **Dual API**: sync + async (CompletableFuture), procedural + functional
- **Statistics**: track all operations
- **Thread safety**: ScopedValues for context propagation, concurrent collections, atomic counters
- **Resource management**: implement AutoCloseable, proper cleanup
- **ONLY service manager uses socket channels and ONLY in non-blocking mode**
- **Protocol implementations NEVER touch SocketChannel/Selector directly** — they use DataChannel
- **Never use Socket and blocking mode in DP/DF/service context**
- **Do not hide issues — throw errors**

---

## Testing

### General Rules
- Use ONLY lego-flow components in unit tests (in-memory transport, self-started servers)
- Tests that connect to external hosts belong in `interop-tests/`
- Use `CountDownLatch` for synchronization, not `Thread.sleep`
- Verify callbacks with latches, not timeouts
- Use `patch`/`write_file` for edits, never chat-blocks for code

> **Full test patterns**: See [doc/AGENTS_test_patterns.md](doc/AGENTS_test_patterns.md)

### CI Behavior
- Interop tests are **disabled by default** — enable with `run-interop` label on PR
- Windows build is **disabled by default** — enable with `run-windows` label on PR
- Coverage gate can be skipped with `skip-coverage` label
- CI uses Node.js 24 (GitHub Actions deprecation of Node.js 20)

---

## Build System Consistency

**CRITICAL**: Two independent build systems (Maven and Gradle) with different module resolution strategies.

### Key Differences
1. **Maven root POM has empty modules list** — child modules built individually
2. **Gradle uses settings.gradle.kts** — fully declares all modules
3. **Dependency groupId is `ssg`** — never `ssg.legoflow`

### Pre-Approved Build Commands
```bash
mvn install -DskipTests -pl '!benchmarks'
./gradlew test
```

### Verification Checklist
- [ ] `mvn compile -DskipTests -pl '!benchmarks'` succeeds
- [ ] `./gradlew test` succeeds
- [ ] No module references `ssg.legoflow` groupId
- [ ] No module references non-existent `legoflow-parent`
- [ ] All leaf modules with tests declare JUnit + SLF4J test dependencies

---

## Standard Module Structure

```
module/
  README.md                 — Overview, features, quick-start, dependency badges
  AGENTS.md                 — Module-specific conventions (references root AGENTS.md)
  CLAUDE.md                 — Symlink: CLAUDE.md -> AGENTS.md
  pom.xml                   — Maven POM
  build.gradle.kts          — Gradle build
  doc/
    ARCHITECTURE.md         — Module purpose, Mermaid diagrams, design decisions
    COMPLIANCE.md           — Spec compliance matrix
    REQUIREMENTS.md         — Historical requirements tracking (append-only)
  src/main/java/
  src/test/java/
```

Every module MUST have all files. New modules without these are **non-compliant**.

### Documentation Format Rules
- **README.md**: Must include shields (Java, Maven, License, Tests, Version). Mermaid or text diagrams only — no ASCII art.
- **doc/ARCHITECTURE.md**: Mermaid diagrams (graph TD, LR, sequenceDiagram) — no ASCII art
- **doc/COMPLIANCE.md**: RFC sections → status → test ref
- **doc/REQUIREMENTS.md**: Commit-based entries (append-only, historical)
- **AGENTS.md**: References root AGENTS.md with relative link

---

## Protocol Implementation Guidelines

1. **Transport-agnostic core** — implement protocol logic through `DataChannel` SPI
2. **Client/server symmetry** — shared codec, separate lifecycle management
3. **Frame codec tests first** — encode/decode round-trip for all wire formats
4. **Use `AmqpEventListener` pattern** — lightweight lifecycle listener with NO_OP default
5. **Session management** — connection → session → link hierarchy
6. **Flow control** — credit-based for messaging, window-based for stream protocols

> **Protocol accuracy rules**: See [doc/AGENTS_protocol_accuracy.md](doc/AGENTS_protocol_accuracy.md)
> **Test patterns**: See [doc/AGENTS_test_patterns.md](doc/AGENTS_test_patterns.md)

---

## CI Workflow

- `cleanup-1`, `master`, `productize` branches trigger CI on push/PR
- `skip-coverage` label skips coverage gate
- `run-interop` label enables interop tests (disabled by default)
- `run-windows` label enables Windows build (disabled by default)
- Node.js 24 forced via `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true`
