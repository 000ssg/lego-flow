# SSH Module -- Requirements

## Initial Implementation (2026-06-26)

### Original Request
> Create a complete SSH module implementing SSH-2 protocol (RFC 4251-4256) from scratch with 17 major areas: transport layer, key exchange (6 algorithms), encryption (6 ciphers), MAC (4 algorithms), compression (3 types), host keys (5 algorithms + utilities), authentication (5 methods + context), connection layer, client API, server API, SFTP subsystem, SCP, demos, 200+ tests, and documentation.

### Reformulated Requirements

1. Full SSH-2 binary packet transport per RFC 4253 section 6
2. Version exchange per RFC 4253 section 4.2
3. Algorithm negotiation via KEXINIT messages per RFC 4253 section 7.1
4. Six key exchange algorithms: DH Group14 (SHA-256), DH Group16 (SHA-512), ECDH P-256/384/521 (RFC 5656), Curve25519 (RFC 8731)
5. Six symmetric ciphers: AES-128/192/256-CTR, AES-128/256-GCM, ChaCha20-Poly1305
6. Four MAC algorithms: HMAC-SHA2-256/512, with ETM variants
7. Three compression modes: none, zlib, zlib@openssh.com (delayed)
8. Five host key algorithms: Ed25519, ECDSA P-256/384, RSA-SHA2-256/512
9. Host key utilities: SshPublicKey (authorized_keys format, fingerprint), SshKeyPair, KnownHosts
10. Five authentication methods: password, public key, keyboard-interactive, host-based, none
11. Server-side AuthContext with password/public key validators
12. Channel multiplexing with window-based flow control (session, direct-tcpip, forwarded-tcpip)
13. Client API with builder-pattern configuration
14. Server API with virtual threads and configurable factories
15. SFTP v3 subsystem client and server
16. SCP client and server
17. Six demo applications
18. 200+ tests covering all components
19. All crypto via standard JCA/JCE (no third-party libraries)
20. Follow existing project patterns (sealed interfaces, records, enums with fromCode/toCode, factories, builders)

### Final Design Decisions

- **Sealed interface for SshPacket**: All 30 transport-level message types as inner records. KexInit kept as separate record due to Java's cross-package sealed restriction.
- **Factory pattern for algorithms**: CipherFactory, MacFactory, HostKeyFactory with create(), isSupported(), supportedAlgorithms() methods.
- **Builder pattern for configs**: SshClientConfig.Builder and SshServerConfig.Builder with sensible defaults.
- **Window management**: AtomicLong-based thread-safe window tracking with auto-adjust threshold at 50%.
- **Virtual threads for server**: Executors.newVirtualThreadPerTaskExecutor() for connection handling.
- **SFTP sealed interface**: 25 packet types as inner records of SftpPacket sealed interface.
- **Standard JCA crypto**: KeyPairGenerator, KeyAgreement, Cipher, Mac, Signature, MessageDigest.

### Implementation Details

**Source files**: 81 Java files across 13 packages
**Test files**: 22 test classes with 319 test methods
**Packages**: transport, kex, cipher, mac, compression, hostkey, auth, connection, client, server, sftp, scp, demo

### Test Coverage

- Transport: 60 tests (version, message types, codec, packets)
- Key exchange: 36 tests (DH Group14/16, ECDH, Curve25519, KexInit)
- Cipher: 14 tests (all ciphers, factory)
- MAC: 14 tests (all MACs, factory)
- Compression: 13 tests (none, zlib, delayed zlib)
- Host keys: 35 tests (all algorithms, public key utilities, known hosts)
- Auth: 23 tests (all methods, context, banner)
- Connection: 13 tests (window manager, channel requests, global requests)
- Client: 27 tests (config builder, client lifecycle)
- Server: 30 tests (config builder, server lifecycle, forwarding filter)
- SFTP: 42 tests (packet types, status codes, file attributes, codec)
- SCP: 7 tests (server sink/source, file I/O)
- **Total: 319 tests, all passing**
