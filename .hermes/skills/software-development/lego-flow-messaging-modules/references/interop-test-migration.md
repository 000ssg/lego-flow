# Interop Test Migration & Debugging

## Service Architecture Migration Pattern

When a messaging module is refactored from `TcpXxxClient` to `XxxClientService`, interop tests break with `ClassNotFoundException`.

### Old Pattern (TcpXxxClient)
Direct TCP client with `connect()`/`close()`, shared across all tests via `@BeforeAll`/`@AfterAll`.

### New Pattern (XxxClientService)
Service architecture with `SelectableChannelManager` for I/O. Each test creates its own service instance.

```java
private static SelectableChannelManager channelManager;

@BeforeAll static void setUpManager() {
    channelManager = new SelectableChannelManager(null);
    channelManager.startEventLoop();
}

@AfterAll static void tearDownManager() throws Exception {
    channelManager.stopEventLoop();
    channelManager.close();
}

private XxxClientService connectClient() {
    var svc = XxxClientService.builder(host, port)
            .username(user).password(pass)
            .build();
    var ctx = new DefaultServiceContext(ServiceUser.anonymous());
    ctx.setAttribute("channelManager", channelManager);
    svc.connect(ctx);
    return svc;
}

private void disconnectClient(XxxClientService svc) {
    var ctx = new DefaultServiceContext(ServiceUser.anonymous());
    ctx.setAttribute("channelManager", channelManager);
    svc.disconnect(ctx);
}
```

### State Transition Pitfall
Calling `connect()` on a service already in `CONNECTING` state throws:
```
StateTransitionException: Invalid state transition: CONNECTING → CONNECTING
```
**Fix**: Always create a fresh service per test. Never reuse a service that failed to fully disconnect.

## Debugging Workflow

**User preference**: Analyze first, propose, then act. Present exact findings (logs, container state, test output) and proposals. Wait for user decision before implementing. Do not deep-dive into speculative fixes.

## Interop Group Runs (local and CI)

Interop tests are selected by JUnit tag group, not by `-Dtest` class lists:

```bash
# one group (the exact CI command):
mvn verify -pl interop-tests -am -DskipTests=true -DskipInteropTests=false -Dinterop.group=interop-messaging-core

# a group with zero tests yet (kafka, wamp):
mvn verify -pl interop-tests -am -DskipTests=true -DskipInteropTests=false -Dinterop.group=interop-wamp -Dinterop.failIfNoTests=false
```

- `skipInteropTests` defaults to `true` — activation must be explicit.
- A typo'd group name FAILS the build — that is the intended guard, not an error to work around.
- **Run the group twice consecutively before declaring stability** — one green run is not proof the suite is non-flaky.
- **Docker daemon down** is a recurring local state: `open -a Docker` (macOS), then `docker start <containers>` — compose containers persist across reboots, the daemon does not.
- **Artemis credentials quirk**: the compose entrypoint reads `ARTEMIS_USER`/`ARTEMIS_PASSWORD` — `ARTEMIS_USERNAME` is silently ignored. The interop module connects on port 5675 with user `artemis`/`guest`.
- **Surefire `systemPropertyVariables` overrides class-code defaults** — the interop pom pins properties (e.g. `interop.amqp.port=5675`) for the forked test JVM; a test class's `System.getProperty(name, "default")` fallback is a red herring. Before concluding a test hits the wrong broker/port, read the pom's Surefire block first.
- **When a client leg dies with a bare "Connection closed", read the BROKER-side log before touching client code** — `docker logs <container>` shows the server's parse error (Kafka: `InvalidRequestException` + the apiKey/version + the allocation size it tried), which localizes the bug to a specific request layout. Cross-check which API versions the failing legs exercise against the plan matrix: legs hitting not-yet-implemented versions (rows still `☐`) are separate sub-tasks, not a regression of the version you just landed — the leg that exercises your version (e.g. ApiVersions negotiation) is the pass/fail signal for your sub-task.
- **Test counts come from source or a live run, not from doc arithmetic** — derive per-class counts with `grep -oE '@(Test|ParameterizedTest|RepeatedTest|TestFactory)' <file>` and totals from the live run; doc totals drift silently (a stale "22" persisted against 19 real tests).
- Verified current pass/fail state lives in `PROGRESS.md` and each module's `doc/COMPLIANCE.md` — do not duplicate it here.

## Docker Scope: One Compose File Per Group

Each interop group owns one compose file with exactly the services it needs — no shared compose file, no runtime-selected subset, no cross-group containers. The files are an exact partition of the services: every service in exactly one group's file, none missing.

Current split (verify against the files; it changes with protocol work):
- `docker-compose.core.yml` — artemis (5675, AMQP 1.0), rabbitmq (61613 STOMP + AMQP 0-9-1), mosquitto (1883 MQTT)
- `docker-compose.kafka.yml` — cp-kafka single-node KRaft
- `docker-compose.wamp.yml` — crossbar router on 8081

- Bring up ONLY the group's file: `docker compose -f <file> up -d`, health-check that group's containers, `docker compose -f <file> down --remove-orphans` after the run.
- **After writing or editing a compose file, always run `docker compose -f <file> config --quiet`** — it catches invalid YAML (duplicate keys) that text-based edits sneak in; when a compose file misbehaves after an edit, treat `cat`/`git show` output as ground truth (`read_file` views can go stale after external edits).
- **A compose service is load-bearing per its OTHER consumers**: before deleting or moving a service, grep the interop test tree for its port — the broker that exists "for test X" may also serve interop test Y and must stay.

## Record-Only Capture Tests Are Removable (checklist)

A capture test that runs an external reference client and dumps wire bytes to files with **zero assertions** is a diagnostic tool, not scope. Retire it once the bug it diagnosed is fixed by an asserting test. Checklist, in order:

1. **Prove it is a recorder**: `grep -c 'assert' <file>` = 0 and no fixture loading — the class only writes files.
2. **Find who loads the fixtures**: grep the capture file names across the repo. A test that *loads* a capture as input is scope; a javadoc mention is not.
3. **Find the asserting coverage**: identify the interop test(s) that hit the same broker + scenario with real assertions; run them against the live broker and confirm green before deleting the recorder.
4. **Check compose services for other consumers** (port grep) — the broker that exists "for the capture tests" may also serve real interop tests and must stay in the group's compose file.
5. **Remove in one commit**: test classes, scenario scripts, capture fixtures (including orphans nothing references), the CI provisioning steps (docker cp / pip) the recorder needed, gitignore entries, and doc counts. KEEP the capture infrastructure (proxy/interceptor + unit tests) — it is the reusable byte-level diagnostic for the next protocol bug.
6. **Verify with `mvn clean verify` on the group** — stale `target/test-classes` re-run deleted recorders and their side effects resurrect the deleted fixtures — and grep for stale references (class names, fixture names, provisioning commands).
