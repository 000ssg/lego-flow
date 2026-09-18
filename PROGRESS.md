# FINAL VERIFICATION — lego-flow (AMQP/MQTT/STOMP refactor)

## GOAL
Fix project lego-flow after AMQP/MQTT/STOMP refactor: verify build/test (incl. interop),
demos, benchmarks. Then update docs (compliance, protocol guidelines, test instructions)
and commit. Do NOT push.

## STATUS (live)
- [x] Gathered context (git, previous session, APIs, diffs)
- [x] Migrate 7 MQTT demo main files + 6 demo tests to new transport API (in-memory pairs)
- [x] Fix demos build failure (old 1-arg MqttClient / broker.bind()/getPort())
- [x] Fix docker-compose artemis creds (guest/amq -> artemis/guest; port 5675)
- [x] Fix interop pom interop.amqp.port 5672 -> 5675 (match artemis; rabbitmq stays 5672)
- [x] FIX PRODUCTION BUG: MqttPipelineTransport.onWrite double-flip (CONNECT dropped on real TCP)
- [x] FIX PRODUCTION BUG: MqttClientService.doConnect illegal CONNECTING->CONNECTING transition
- [x] Build (Maven install skipTests) — PASS
- [x] Unit tests: messaging/mqtt 355 pass, service module PASS, demos (MQTT) 26 pass
- [x] Interop (Docker) MQTT 3/3 PASS (real Mosquitto), STOMP 3/3, AMQP AmqpInteropTest 6/6 (in flight)
- [x] New MqttMosquittoInteropTest (real broker, MqttClientService, @Tag messaging-protocols)
- [x] Run interop group AmqpInteropTest+MqttMosquitto+Stomp in one shot — PASS 12/12 twice (2026-09-18)
- [x] Full unit test (Maven + Gradle) — Maven all modules BUILD SUCCESS (771 in demos run; stomp 233); Gradle clean test BUILD SUCCESSFUL
- [x] Demos run — 771 tests, 0 failures
- [x] Benchmarks run — build + tests BUILD SUCCESS
- [x] Update docs (stomp COMPLIANCE/ARCHITECTURE/README + root REQUIREMENTS entry + PROGRESS)
- [ ] Commit (no push) — next step

## KEY FACTS (verified this session)
- Branch: cleanup-messaging. Two build systems (Maven empty modules list, Gradle settings.gradle.kts). groupId=ssg.
- MqttClient/MqttBroker are TRANSPORT-AGNOSTIC (take MqttTransport). broker.handleConnection(MqttTransport).
  MqttClient(config, transport) 2-arg ctor. Old socket API removed (commit b49be9dc).
- InMemoryMqttTransport.createPair() -> MqttTransport[2]; broker=pair[0], client=pair[1]. Use transport/ not codec/ dup.
- MqttCodec.encode() -> encodeWithHeader() returns buffer in READ mode (flipped). NEVER flip again on write.
  STOMP/AMQP PipelineTransport.onWrite check hasRemaining() and do NOT flip — MQTT now matches.
- SelectableChannelManager: ALL TCP lifecycle in selector thread. finishConnect -> OP_READ|OP_WRITE.
  fireRead -> pipeline.onRead -> transport ring buffer. fireWrite -> onWrite.
- AMQP: Artemis SASL-first (proto-3). Interop target Artemis on 5675, user artemis/guest (ARTEMIS_USER entrypoint;
  ARTEMIS_USERNAME env ignored). Verified 2026-09-10. RabbitMQ AMQP 0.9.1 on 5672 (guest/guest) is a separate wire-capture target.
- STOMP: RabbitMQ STOMP 61613 (guest/guest), StompClientService service-based.
- MQTT interop: NEW real-broker test MqttMosquittoInteropTest against Mosquitto 1883 via MqttClientService.

## PRODUCED BUG FIXES (this session)
1. MqttPipelineTransport.onWrite: removed `buf.flip()`. Codec returns read-mode buffer; double flip made
   remaining()==0 so connect.send() wrote 0 bytes -> broker never got CONNECT -> client timeout on CONNACK.
   In-memory path unaffected (no flip), which is why demos passed while real TCP hung. Now guards hasRemaining()
   and drains remaining bytes, re-queueing any unwritten tail (mirrors STOMP/AMQP).
2. MqttClientService.doConnect: removed `transitionTo(ProcessorState.CONNECTING)`. Base AbstractService.connect()
   already transitioned to CONNECTING; the duplicate was an illegal self-transition throwing on real connect.
   STOMP client service does NOT re-transition (reference pattern).
3. STOMP StompFrameCodec.receive() (root cause of testMultipleMessages 0/5 in combined interop run):
   old code drained the whole read buffer per call but decoded only the FIRST frame — with real TCP a read
   returns all batched bytes (5 MESSAGE frames arrive in one ~64KB read after batching), so 4 of 5 messages
   were silently dropped; partial frames threw and killed the receiver thread. Rewrote receive() as a stream
   reassembler using StompCodec.findFrameEnd(): accumulate all bytes, decode exactly one complete frame per
   call, keep the remainder; FrameIncompleteException = wait for more bytes; timeout with no data does NOT
   end the loop (only transport close returns null). 6 new regression tests in StompFrameCodecTest using a
   chunked fake transport (batched/split/trailing/heartbeat/timeout/close). STOMP unit tests 233 pass;
   messaging interop group 12/12 pass twice in a row.

## BLOCKERS / CAVEATS
- Full interop `mvn verify -DskipInteropTests=false` runs ALL 12 protocols (nginx, redis, pg, sshd, nats, xmpp,
  ldap, ftp, smtp...) — only 3 messaging brokers are in this branch's compose, so the full 12-protocol run can't
  pass locally. The coherent unit is the @Tag("messaging-protocols") group: AmqpInteropTest + MqttMosquittoInteropTest
  + StompInteropTest. That's what we run.
- Amqp10WireCaptureTest needs `docker cp` of artemis jar + hardcoded 5675 guest/guest (separate from artemis/guest).
  AmqpWireCaptureTest is RabbitMQ 5672 guest/guest. Both are untagged wire-capture reference tools, excluded from the
  tagged messaging-protocols run.
- PROGRESS.md was stale (showed statuses as [x] PASS before they were). Now corrected.

## ENV
- JAVA_HOME=$HOME/.sdkman/candidates/java/current
- Build: `export JAVA_HOME=$HOME/.sdkman/candidates/java/current && mvn install -DskipTests -pl '!benchmarks' --no-transfer-progress -q`
- Unit tests: `./gradlew test` and/or `mvn test`
- Interop: docker compose -f interop-tests/docker-compose.yml up -d ; mvn test -pl interop-tests -DskipInteropTests=false -Dtest='...'
- Docker: daemon UP. Containers: artemis-test(5675, artemis/guest), rabbitmq-test(5672+61613, guest/guest), mosquitto-test(1883).
  CWD for compose: interop-tests/
- Note: `timeout` is NOT available on macOS; use background + poll, or a Java-level latch.
