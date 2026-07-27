# SSH Module -- Development Guide

## Module Purpose

The `ssh` module implements the SSH-2 protocol (RFC 4251-4256) for secure remote access. It provides both client and server implementations with support for key exchange, authentication, channels, port forwarding, SFTP, and SCP, built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `SshClient` -- client with connect, authenticate, session channels, port forwarding, SFTP
- `SshServer` -- server with virtual thread per connection, host keys, authentication context
- `SshTransport` -- binary packet transport layer with cipher/MAC/compression negotiation
- `SshTransportCodec` -- binary packet encoding/decoding per RFC 4253 section 6
- `KexAlgorithm` -- key exchange interface (DH Group14/16, ECDH P-256/384/521, Curve25519)
- `SshCipher` -- cipher interface (AES-CTR 128/192/256, AES-GCM 128/256, ChaCha20-Poly1305)
- `SshMac` -- MAC interface (HMAC-SHA2-256/512, ETM variants)
- `SshChannel` -- abstract channel with window-based flow control
- `SshAgent` -- in-memory SSH agent with identity management and signing
- `SshCertificate` -- OpenSSH certificate parsing, encoding, and validation
- `CertificateHostKeyAlgorithm` -- certificate-based host key algorithm wrapping standard algorithms
- `GssApiAuth` -- GSSAPI "gssapi-with-mic" authentication per RFC 4462
- `SftpClient` / `SftpServer` -- SFTP subsystem version 3
- `ScpClient` / `ScpServer` -- SCP file transfer

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `transport` | Transport layer: version exchange, binary packet codec, message types, sealed packet hierarchy |
| `kex` | Key exchange: DH Group14 (SHA-256), DH Group16 (SHA-512), ECDH P-256/384/521, Curve25519 |
| `cipher` | Encryption: AES-128/192/256-CTR, AES-128/256-GCM, ChaCha20-Poly1305, CipherFactory |
| `mac` | Message authentication: HMAC-SHA2-256/512, ETM variants, MacFactory |
| `compression` | Compression: none, zlib, zlib@openssh.com (delayed) |
| `hostkey` | Host keys: Ed25519, RSA-SHA2-256/512, ECDSA P-256/384, KnownHosts, SshPublicKey, certificate-based (SshCertificate, CertificateHostKeyAlgorithm, CertType) |
| `auth` | Authentication: password, public key, keyboard-interactive, host-based, none, gssapi-with-mic, AuthContext |
| `agent` | SSH agent: in-memory agent (SshAgent), agent protocol messages (SshAgentMessage), codec (SshAgentCodec), agent forwarding channel (AgentForwardingChannel) |
| `connection` | Connection layer: session/direct-tcpip/forwarded/x11 channels, window management, global requests, X11ForwardingConfig |
| `client` | Client API: SshClient, SshClientConfig (builder pattern) |
| `server` | Server API: SshServer, SshServerConfig, ShellFactory, CommandFactory, ForwardingFilter |
| `sftp` | SFTP subsystem: packet types, codec, file attributes, client, server |
| `scp` | SCP protocol: file upload/download, directory upload |
| `demo` | Demo applications: simple connect, terminal, port forwarding, SCP, SFTP, server |

## SSH-Specific Coding Conventions

### Protocol Message Types
- Transport (1-21): DISCONNECT, IGNORE, UNIMPLEMENTED, DEBUG, SERVICE_REQUEST, SERVICE_ACCEPT, NEWKEYS
- Key Exchange (20-31): KEXINIT, KEXDH_INIT, KEXDH_REPLY, KEX_ECDH_INIT, KEX_ECDH_REPLY
- Authentication (50-61): USERAUTH_REQUEST, USERAUTH_FAILURE, USERAUTH_SUCCESS, USERAUTH_BANNER
- Connection (80-100): GLOBAL_REQUEST, CHANNEL_OPEN, CHANNEL_DATA, CHANNEL_CLOSE, etc.

### Crypto Primitives
- All crypto uses `java.security`, `javax.crypto`, `java.security.spec` (no third-party crypto)
- Key exchange uses `KeyPairGenerator`, `KeyAgreement`
- Ciphers use `javax.crypto.Cipher` with `AES/CTR/NoPadding`, `AES/GCM/NoPadding`, `ChaCha20`
- MACs use `javax.crypto.Mac` with `HmacSHA256`, `HmacSHA512`
- Signatures use `java.security.Signature` with `SHA256withECDSA`, `Ed25519`, `SHA256withRSA`

### Window Management
- Default window size: 2 MB
- Default max packet size: 32 KB
- AtomicLong for thread-safe window tracking
- Auto-adjust when local window falls below half initial size

### Sealed Interface Pattern
- `SshPacket` -- sealed interface with 30 inner record implementations (all in transport package)
- `SftpPacket` -- sealed interface with 25 inner record implementations
- `AuthResult` -- sealed interface with Success, Failure, Continuation variants
- `KexInit` is a separate record (not part of SshPacket due to cross-package sealed restriction)

## Testing Practices

- Unit tests for every crypto component: encrypt/decrypt roundtrip, property verification
- Key exchange tests: shared secret agreement between client/server instances
- Codec tests: encode/decode roundtrip for transport and SFTP packets
- Factory tests: algorithm creation, supported/unsupported checks
- Config tests: builder defaults, custom values, immutability
- SCP server tests: sink/source with actual file I/O via @TempDir
- All tests use loopback or in-memory constructs (no external SSH server required)
- Test count: 430

---

**Last Updated**: 2026-06-26
**For AI assistant versions**
