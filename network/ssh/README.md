# Lego Flow SSH

![Java](https://img.shields.io/badge/Java-24%2B-blue)
![Maven](https://img.shields.io/badge/Maven-3.9%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Tests](https://img.shields.io/badge/Tests-430-brightgreen)
![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-orange)

SSH-2 protocol implementation (RFC 4251-4256) for the Lego Flow framework. Provides complete client and server APIs with support for modern cryptographic algorithms, channel multiplexing, port forwarding, SFTP, and SCP.

## Features

- **Transport Layer**: Binary packet encoding/decoding, version exchange, algorithm negotiation
- **Key Exchange**: Curve25519-SHA256, ECDH (P-256, P-384, P-521), DH Group14/16
- **Encryption**: ChaCha20-Poly1305, AES-256/128-GCM, AES-256/192/128-CTR
- **MAC**: HMAC-SHA2-512/256 with encrypt-then-MAC support
- **Compression**: None, zlib, zlib@openssh.com (delayed activation)
- **Host Keys**: Ed25519, ECDSA (P-256, P-384), RSA-SHA2-256/512, certificate-based (OpenSSH certs)
- **Authentication**: Password, public key, keyboard-interactive, host-based, none, GSSAPI (gssapi-with-mic)
- **Channels**: Session, direct-tcpip, forwarded-tcpip, x11, auth-agent with window-based flow control
- **Agent Forwarding**: In-memory SSH agent with identity management and signing
- **X11 Forwarding**: X11 channel and configuration per RFC 4254 section 6.3.1
- **SFTP**: Version 3 client and server with full file operations
- **SCP**: File and directory upload/download
- **Server**: Virtual thread per connection, configurable auth and forwarding policies

## Quick Start

### Client

```java
try (SshClient client = new SshClient()) {
    client.connect("example.com", 22);
    client.authenticate("user", new PasswordAuth("secret"));
    
    SessionChannel session = client.openSession();
    session.requestExec("ls -la");
    // read output from session...
}
```

### Server

```java
try (SshServer server = new SshServer(SshServerConfig.builder().port(2222).build())) {
    server.setHostKey(SshKeyPair.generate(new Ed25519()));
    server.setAuthenticator(authContext);
    server.setShellFactory((in, out, err) -> { /* handle shell */ });
    server.bind();
}
```

### SFTP

```java
SessionChannel sftpChannel = client.openSftpChannel();
SftpClient sftp = new SftpClient(sftpChannel);
sftp.init();
// sftp.open(), sftp.read(), sftp.write(), sftp.stat(), etc.
```

## Module Structure

```
ssh/
  src/main/java/ssg/legoflow/ssh/
    transport/     -- Binary packet transport, message types, codec
    kex/           -- Key exchange algorithms (DH, ECDH, Curve25519)
    cipher/        -- Symmetric ciphers (AES-CTR, AES-GCM, ChaCha20-Poly1305)
    mac/           -- MAC algorithms (HMAC-SHA2-256/512, ETM variants)
    compression/   -- Compression (none, zlib, zlib@openssh.com)
    hostkey/       -- Host key algorithms, known hosts, public key utilities, certificates
    auth/          -- Authentication methods and server-side context
    agent/         -- SSH agent protocol, in-memory agent, agent forwarding channel
    connection/    -- Channel multiplexing, window management, forwarding, X11
    client/        -- SSH client API with builder configuration
    server/        -- SSH server API with virtual threads
    sftp/          -- SFTP v3 subsystem (client + server)
    scp/           -- SCP file transfer (client + server)
    demo/          -- Example applications
```

## Supported Algorithms

| Category | Algorithms |
|----------|-----------|
| Key Exchange | curve25519-sha256, ecdh-sha2-nistp256/384/521, diffie-hellman-group14-sha256, diffie-hellman-group16-sha512 |
| Host Key | ssh-ed25519, ecdsa-sha2-nistp256/384, rsa-sha2-256, rsa-sha2-512, ssh-ed25519-cert-v01@openssh.com, ecdsa-sha2-nistp256-cert-v01@openssh.com, rsa-sha2-256-cert-v01@openssh.com |
| Cipher | chacha20-poly1305@openssh.com, aes256-gcm@openssh.com, aes128-gcm@openssh.com, aes256-ctr, aes192-ctr, aes128-ctr |
| MAC | hmac-sha2-512-etm@openssh.com, hmac-sha2-256-etm@openssh.com, hmac-sha2-512, hmac-sha2-256 |
| Compression | none, zlib@openssh.com, zlib |

## Dependencies

- `lego-flow-blocks` -- Core data processing framework
- `lego-flow-service` -- Service framework for TCP transport
- SLF4J -- Logging facade
- JDK 24+ standard crypto libraries (no third-party crypto)

## Testing

430 tests covering all components:
- Transport layer encoding/decoding
- Key exchange shared secret agreement
- Cipher encrypt/decrypt roundtrip
- MAC compute/verify
- Compression compress/decompress
- Host key sign/verify
- Authentication encoding
- GSSAPI authentication encoding
- SFTP packet codec
- SCP server file I/O
- Client/server configuration
- SSH agent identity management and signing
- Agent protocol codec encode/decode roundtrip
- X11 forwarding configuration and channel
- Certificate parsing, encoding, issuance, and verification

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
