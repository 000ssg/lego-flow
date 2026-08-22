# Lego Flow — Persistent Working State

## PROJECT OVERVIEW
- **Path**: /Users/sergey.sidorov/work/projects/github/lego-flow
- **Build**: Maven (interop-tests), Gradle (core modules)
- **Java**: JDK 25
- **Goal**: Enable all SSH and Telnet interop tests + implement AMQP 0-9-1 for RabbitMQ production support

## INTEROP TEST RESULTS (Last run: 2026-08-21)
```
Telnet: Tests run: 29, Failures: 0, Errors: 0, Skipped: 1 ✅
AMQP:  In progress — AMQP 0-9-1 module scaffolded (see .interop-state-checkpoint.md)
```

## STATUS SUMMARY
- **Telnet**: COMPLETE — All interop tests pass against wistic/telnetd (telnet client) and local TelnetServer (telnet server)
- **SSH**: IN PROGRESS — Key exchange implementation needed (see SSH REVIEW SESSION below)
- **AMQP 0-9-1**: IN PROGRESS — Module scaffolded with core frame codec, SASL PLAIN, client skeleton
- **AMQP 1.0**: EXISTS but DISABLED — SASL flow incompatible with RabbitMQ; AMQP 0-9-1 needed for RabbitMQ


```
Tests run: 184, Failures: 0, Errors: 5, Skipped: 0
Errors:
  - AmqpInteropTest.connect → Connection refused (port mismatch)
  - SshServerInteropTest.testOurClientAuthenticatesWithOpenSSHD → "Unexpected end of stream"
  - SshServerInteropTest.testOurClientExecutesCommandOnOpenSSHD → "Unexpected end of stream"
  - SshServerInteropTest.testServerConnectionCount → SocketTimeout (readNBytes issue)
  - TelnetServerInteropTest.testRawEchoProtocolWithTelnetd → SocketTimeout (telnet negotiation)
```

## DOCKER CONTAINER STATUS
| Container | Port Mapping | Status |
|-----------|-------------|--------|
| legoflow-rabbitmq | 25672→5672, 15672→15672 | Up, healthy, AMQP 1.0 plugin enabled |
| legoflow-sshd | 2222→22 | Up, sshd running, user legoflow/legoflow |
| legoflow-telnetd | 2223→23 | Up, wistic/telnetd |
| legoflow-activemq | 5673→5672, 61613→61613 | Up, healthy (AMQP 1.0) |
| legoflow-nats | 4222→4222 | Up, healthy |
| legoflow-mosquitto | 1883→1883 | Up, healthy |
| legoflow-redis | 6379→6379 | Up, healthy |
| legoflow-postgresql | 5432→5432 | Up, healthy |
| legoflow-nginx | 8080→80 | Up, healthy |
| legoflow-openldap | 389→389 | Up, healthy |
| legoflow-prosody | 5222→5222 | Up, healthy |
| legoflow-mailhog | 25→1025, 8025→8025 | Up, healthy |
| legoflow-ftp | 21→21, 30000-30009→30000-30009 | Up, healthy |

## OPENSSH SERVER DETAILS (sshd container)
- OpenSSH_8.9p1 Ubuntu-3ubuntu0.16
- Key exchange algorithms: curve25519-sha256, ecdh-sha2-nistp256/384/521, diffie-hellman-group14-sha256, diffie-hellman-group-exchange-sha256, etc.
- Host keys: rsa-sha2-512, rsa-sha2-256, ecdsa-sha2-nistp256, ssh-ed25519
- PasswordAuthentication: yes (set via Dockerfile sed)
- KbdInteractiveAuthentication: no
- PermitRootLogin: no
- User: legoflow/legoflow (password)

## TELNETD CONTAINER
- Image: wistic/telnetd:latest
- Port: 2223→23
- Known issue: requires IAC negotiation before echoing data

## RABBITMQ DETAILS
- Version: 4.3.5
- AMQP 1.0 plugin: enabled and running (rabbitmq_amqp1_0 4.3.5)
- AMQP 0-9-1: available natively on port 5672
- User: guest/guest
- Default vhost: /

## CRITICAL SSH CLIENT BUG — Root Cause Identified

### Location: `network/ssh/src/main/java/ssg/legoflow/ssh/client/SshClient.java`
### Method: `connect()` (lines ~76-108)

The SSH client key exchange is INCOMPLETE. Current flow:
```
1. TCP connect
2. Version exchange
3. Send KEXINIT
4. Read remote KEXINIT
5. negotiateAlgorithms()
6. sendNewKeys()          ← WRONG! No DH/ECDH exchange happened!
7. Read NEWKEYS from server
8. Service request
```

**What should happen after step 5 (negotiateAlgorithms):**
```
5a. Create KexAlgorithm instance (e.g., DiffieHellmanGroup14)
5b. Send SSH_MSG_KEXDH_INIT (msg type 30) with public value
5c. Read SSH_MSG_KEXDH_REPLY (msg type 31) with server host key, public value, signature
5d. Compute shared secret via kexAlgorithm.computeSharedSecret(remotePublicKey)
5e. Compute exchange hash via kexAlgorithm.computeExchangeHash(...)
5f. Apply new keys: transport.applyNewKeys(new KexResult(...))
5g. Send SSH_MSG_NEWKEYS (msg type 21)
5h. Read NEWKEYS from server
```

### Missing Methods in SshTransport.java:
- `performKeyExchange(KexInit localKexInit, KexInit remoteKexInit)` — the whole DH flow
- No `createKexAlgorithm(String name)` factory method

### Key Exchange Algorithm Implementations Available:
- `DiffieHellmanGroup14` (name: "diffie-hellman-group14-sha256")
- `DiffieHellmanGroup16` (name: "diffie-hellman-group16-sha512")
- `EcdhSha2Nistp256`, `EcdhSha2Nistp384`, `EcdhSha2Nistp521`
- `KexAlgorithm` interface: name(), hashAlgorithm(), init(), localPublicValue(), computeSharedSecret(), computeExchangeHash()
- `KexResult` record: sharedSecret, exchangeHash, sessionId

### SshTransportCodec helper methods:
- `readBinary(ByteBuffer)` — reads mpint (length-prefixed bytes)
- `readString(ByteBuffer)` — reads length-prefixed string
- `readBoolean(ByteBuffer)` — reads boolean
- `writeBinary(ByteBuffer, byte[])` — writes mpint
- `writeString(ByteBuffer, String)` — writes string

## SSH SERVER — `testServerConnectionCount` Timeout
### Location: `interop-tests/src/test/java/ssg/legoflow/interop/ssh/SshServerInteropTest.java` line 308
### Issue: `socket.getInputStream().readNBytes(40)` times out
### Likely cause: Server is not sending version string because the client socket doesn't trigger the version exchange properly. The server may need the client to connect and wait for the version to be sent. Need to check if `readNBytes` blocks waiting for exactly 40 bytes when server hasn't sent any data yet.

## TELNET CLIENT TEST ISSUE
### Location: `interop-tests/src/test/java/ssg/legoflow/interop/telnet/TelnetServerInteropTest.java` line 209
### Test: `testRawEchoProtocolWithTelnetd`
### Issue: SocketTimeoutException — sends "Hello\r\n" but server doesn't echo
### Root cause: wistic/telnetd requires proper telnet negotiation (IAC WILL/WONT) before echoing
### Fix: The test should either:
  a) Send proper telnet negotiations before sending data
  b) Use raw socket mode without expecting echo (just verify connection works)
  c) Send IAC ECHO first, then IAC WONT ECHO to disable echo from server

## AMQP 1.0 ISSUE — Connection Refused
### Location: `interop-tests/src/test/java/ssg/legoflow/interop/amqp/AmqpInteropTest.java`
### Test: `connect()` — `client.connect()`
### Root cause: docker-compose maps 25672→5672, but tests connect to localhost:5672
### Fix: Either change docker-compose port mapping to 5672:5672 OR update test to use port 25672
### Additional issue: RabbitMQ 4.x AMQP 1.0 plugin may use different SASL flow than ISO 19464
### Existing mitigation: `rabbitmqCompat(true)` in ClientConfig — but connection never succeeds due to port issue

## AMQP 0-9-1 — NOT IMPLEMENTED (Primary User Request)
### Status: No implementation exists. messaging/amqp is AMQP 1.0 only.
### Need: Create `messaging/amqp091` module
### Wire format: Frame = `AMQP\x00\x0A\x00\x00` header + frame type (1 byte) + channel (2 bytes) + payload size (4 bytes) + payload + frame-end (1 byte = 0x0A)
### Protocol flow: connection.start → start-ok → connection.tune → tune-ok → connection.open → channel.open → queue.declare → basic.publish → basic.consume
### SASL: PLAIN mechanism (can reuse from messaging/amqp)

## RESTRICTIONS & DECISIONS
- Host preference: NEVER use backslash line continuations in GitHub Actions YAML
- Host preference: Don't commit/push changes automatically
- DO NOT add inline comments within code unless explicitly requested
- DO NOT commit/push changes automatically
- DO NOT add one-letter variable names unless explicitly requested
- JAVA 25 — use virtual threads (Thread.ofVirtual())

## PLAN STATUS
1. ✅ Create WORK_STATE.md (this file) — DONE
2. ⏳ Fix SSH client key exchange — IN PROGRESS (root cause identified, implementation needed)
3. ⏳ Fix telnet client test — PENDING
4. ⏳ Fix AMQP interop port mapping — PENDING
5. ⏳ Implement AMQP 0-9-1 client module — PENDING
6. ⏳ Implement AMQP 0-9-1 interop test — PENDING
7. ⏳ Run full interop test suite — PENDING

## BUILD COMMANDS
```bash
# Compile SSH module
mvn compile -pl network/ssh -am -q

# Run SSH interop tests only
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dit.test=SshServerInteropTest

# Run telnet interop tests only
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dit.test=TelnetServerInteropTest

# Run AMQP interop tests only
mvn verify -pl interop-tests -am -DskipInteropTests=false -Dit.test=AmqpInteropTest

# Run all interop tests
mvn verify -pl interop-tests -am -DskipInteropTests=false
```

## KEY CODE REFERENCE LOCATIONS
- SSH Client: `network/ssh/src/main/java/ssg/legoflow/ssh/client/SshClient.java`
- SSH Transport: `network/ssh/src/main/java/ssg/legoflow/ssh/transport/SshTransport.java`
- SSH Transport Codec: `network/ssh/src/main/java/ssg/legoflow/ssh/transport/SshTransportCodec.java`
- KEX Interface: `network/ssh/src/main/java/ssg/legoflow/ssh/kex/KexAlgorithm.java`
- KEX Result: `network/ssh/src/main/java/ssg/legoflow/ssh/kex/KexResult.java`
- DH Group14: `network/ssh/src/main/java/ssg/legoflow/ssh/kex/DiffieHellmanGroup14.java`
- SSH Server Interop Test: `interop-tests/src/test/java/ssg/legoflow/interop/ssh/SshServerInteropTest.java`
- Telnet Interop Test: `interop-tests/src/test/java/ssg/legoflow/interop/telnet/TelnetServerInteropTest.java`
- AMQP Interop Test: `interop-tests/src/test/java/ssg/legoflow/interop/amqp/AmqpInteropTest.java`
- AMQP Client: `messaging/amqp/src/main/java/ssg/legoflow/messaging/amqp/client/AmqpClient.java`
- AMQP Client Config: `messaging/amqp/src/main/java/ssg/legoflow/messaging/amqp/client/ClientConfig.java`
- AMQP Constants: `messaging/amqp/src/main/java/ssg/legoflow/messaging/amqp/common/AmqpConstants.java`
- Docker Compose: `interop-tests/docker-compose.yml`
- SSH Dockerfile: `interop-tests/docker/sshd/Dockerfile`
- Settings: `settings.gradle.kts`

## SSH REVIEW SESSION — 2026-08-21

### Audit findings (current state)

**CRITICAL: Build does not compile**
- `SshTransport` missing `performKeyExchange(String, String, byte[], byte[])` — called from SshClient:110 and SshServer:278
- `SshTransport` missing `setServerHostKey(SshKeyPair)` — called from SshServer:276
- `SshTransport` missing `kexAlgorithm()` getter — referenced in SshDebugTest.java
- `SshTransport.applyNewKeys()` does NOT initialize cipher/MAC — commented "keys would be derived here"
- `SshTransportCodec` has `System.out.println` debug statements (packet encoding/decoding)
- `SshClient.connect()` calls `performKeyExchange()` after `negotiateAlgorithms()` — KEY EXCHANGE NOT IMPLEMENTED
- `SshServer.handleConnection()` calls `performKeyExchange()` after `negotiateAlgorithms()` — same gap

**Missing full key exchange flow (both client and server):**
1. negotiateAlgorithms() ✅ exists
2. Create KexAlgorithm instance from negotiated kexAlgorithm name ❌ MISSING
3. kexAlgorithm.init() ❌ NOT CALLED
4. Send KEX_INIT (DH_INIT / ECDH_INIT) ❌ MISSING
5. Read KEX_REPLY (DH_REPLY / ECDH_REPLY with host key + signature) ❌ MISSING
6. Compute shared secret ❌ NOT PERFORMED
7. Compute exchange hash H ❌ NOT PERFORMED
8. Derive session keys (K_c2s, K_s2c, IC_c2s, IC_s2c, MAC_c2s, MAC_s2c) per RFC 4253 §7.2 ❌ MISSING
9. Apply new keys: cipher/MAC init, sequence number reset ❌ MISSING
10. Send SSH_MSG_NEWKEYS ✅ exists (sendNewKeys)
11. Read SSH_MSG_NEWKEYS ✅ exists (readPacket)

**DP/DF service gaps:**
- SshService.doHandshake() calls transport.sendNewKeys() directly without performing DH/ECDH — WILL FAIL with real servers
- SshService does NOT start a reader thread to process incoming encrypted packets
- SshService.convertToOutput processes inbound but no background dispatch loop
- SshClientService lacks reader thread entirely

**Test coverage gaps:**
- No unit tests for key exchange flow (ECDH/DH)
- No tests for cipher/MAC initialization
- No tests for encrypted packet encoding/decoding roundtrip
- Interop tests reference performKeyExchange which doesn't compile
- SshClientOpenSshTest can't run (compilation fails)
- No Junit-based reference server/client for self-testing
- Debug tests use System.out.println throughout

**SshTransportCodec issues:**
- Has `System.out.println` debug output for packet encoding/decoding (line ~124 and decode path)
- DEBUG print in encode path prints AEAD/blockSize info
- DEBUG print in decode path prints sequence number transition
- No debug helper class for packet-level logging

### Decisions

- **D9**: Implement full `performKeyExchange()` in SshTransport — both client and server variants
- **D10**: Add `setServerHostKey()` and `kexAlgorithm()` to SshTransport
- **D11**: Implement RFC 4253 §7.2 key derivation in `applyNewKeys()`
- **D12**: Add reader thread to SshService for DP/DF mode packet dispatch
- **D13**: Remove all `System.out.println` debug statements from SshTransportCodec
- **D14**: Use SLF4J debug-level logging throughout (add packet-level debug helper)
- **D15**: Add JUnit-based reference server (SshServer) + reference client (SshClient) self-tests
- **D16**: Both socket-mode and DP/DF-mode tests must cover key exchange, auth, data transfer
- **D17**: Add test for all KEX algorithms: curve25519-sha256, ecdh-sha2-nistp256, dh-group14-sha256, dh-group16-sha512
- **D18**: Add test for all ciphers: chacha20, aes256-gcm, aes128-gcm, aes256-ctr, aes128-ctr
- **D19**: Verify `SshClientConfig` already provides cipher/mac/kex preference lists
- **D20**: Add reference SSH server (lwssh3 or jsch-based) for interop testing
