# Lego Flow FTP Module

[![JDK](https://img.shields.io/badge/JDK-24-blue)](https://openjdk.org/)
[![Tests](https://img.shields.io/badge/tests-386-green)](src/test/)
[![RFC 959](https://img.shields.io/badge/RFC-959-orange)](https://datatracker.ietf.org/doc/html/rfc959)
[![RFC 4217](https://img.shields.io/badge/RFC-4217-orange)](https://datatracker.ietf.org/doc/html/rfc4217)

FTP (File Transfer Protocol) and FTPS (FTP over TLS) implementation from scratch, supporting both client and server with pluggable filesystems and authentication.

## Features

- **Full FTP client**: connect, login, upload, download, list, rename, delete, mkdir, rmdir
- **FTP server**: virtual threads, pluggable filesystem, configurable authentication
- **FTPS support**: explicit (AUTH TLS) and implicit (port 990) modes
- **Active and passive data connections**: PORT, EPRT, PASV, EPSV
- **Directory listing parsers**: Unix ls -l, Windows DIR, MLSD (RFC 3659)
- **Transfer modes**: ASCII (with CRLF conversion) and binary
- **In-memory filesystem**: for testing and virtual servers
- **Local filesystem**: with chroot security (path traversal protection)
- **RFC compliance**: 959, 2389, 2428, 3659, 4217

## Quick Start

### FTP Client

```java
try (var client = new FtpClient()) {
    client.connect("ftp.example.com", 21);
    client.login("user", "password");
    client.setPassiveMode(true);
    client.setTransferType(FtpTransferType.BINARY);

    // Upload
    client.put(Path.of("local/file.zip"), "/remote/file.zip");

    // Download
    client.get("/remote/file.zip", Path.of("downloaded.zip"));

    // List files
    List<FtpFileEntry> files = client.list("/remote");
}
```

### FTP Server

```java
var config = FtpServerConfig.builder()
    .host("0.0.0.0").port(2121).build();
var fs = new InMemoryFileSystem();

try (var server = new FtpServer(config)) {
    server.setFileSystem(fs);
    server.setAuthenticator(FtpAuthenticator.singleUser("user", "pass"));
    server.start();
}
```

### FTPS Client

```java
try (var client = new FtpClient()) {
    client.connect("ftps.example.com", 21);
    client.enableTls(FtpsConfig.trustAll());
    client.login("user", "password");
}
```

## Packages

| Package | Description |
|---------|-------------|
| `protocol` | FTP commands, reply codes, transfer types, codec |
| `data` | Active/passive data connections, transfer handling |
| `security` | FTPS/TLS configuration and negotiation |
| `client` | FTP client, directory listing parsers |
| `server` | FTP server, filesystem implementations |
| `demo` | Example applications |

## Dependencies

- `lego-flow-blocks` (core data processing)
- `lego-flow-service` (TCP transport)
- SLF4J (logging)
- JUnit 5, AssertJ (testing)

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
