# network / ftp — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `ftp` module implements FTP (RFC 959) and FTPS (RFC 4217) protocols from scratch. It provides both client and server implementations with pluggable filesystems, TLS security, and active/passive data connections.

**NOTE**: SFTP (SSH File Transfer Protocol) is NOT part of this module — SFTP runs over SSH and lives in the `ssh` module.

## Key Interfaces

- `FtpClient` — full FTP client: connect, login, list, upload, download, rename, delete, FTPS
- `FtpServer` — FTP server with virtual threads, pluggable filesystem and authentication
- `FtpProtocolCodec` — text-based protocol codec for commands and replies (CRLF terminated)
- `FtpFileSystem` — virtual filesystem interface (LocalFileSystem, InMemoryFileSystem)
- `FtpAuthenticator` — @FunctionalInterface for user authentication
- `FtpsHandler` — TLS negotiation: AUTH TLS, PBSZ, PROT commands

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | FTP command enum, reply codes, transfer types, structure, mode, codec |
| `data` | Active/passive data connections, data transfer with ASCII/binary modes |
| `security` | FTPS/TLS configuration, handshake handler, implicit/explicit modes |
| `client` | FTP client, LIST parser (Unix/Windows), MLSD parser (RFC 3659) |
| `server` | FTP server, session management, command handler, filesystem implementations |
| `demo` | Example applications: client, server, FTPS, file transfer, virtual FS |

## FTP-Specific Coding Conventions

### Protocol Format
- **Control channel**: text-based, CRLF terminated
- **Commands**: `COMMAND SP argument CRLF`
- **Replies**: `code SP text CRLF` or multi-line `code-text CRLF ... code SP text CRLF`
- **Two connections**: control (port 21) and data (port 20 or dynamic)

### Data Connection Modes
- **Active mode** (PORT/EPRT): server connects TO client
- **Passive mode** (PASV/EPSV): client connects TO server
- Default: passive mode (firewall-friendly)

### Transfer Types
- **ASCII** (TYPE A): line ending conversion (CRLF on wire, local OS on disk)
- **BINARY** (TYPE I): byte-for-byte, no conversion
- **EBCDIC** (TYPE E): for mainframe hosts

### FTPS Security (RFC 4217)
- **Explicit FTPS**: AUTH TLS on port 21 (upgrade plain connection)
- **Implicit FTPS**: TLS from start on port 990
- **PBSZ 0**: protection buffer size (always 0 for TLS)
- **PROT P**: protect data channel with TLS
- **PROT C**: clear data channel

### Directory Listings
- **LIST**: human-readable (Unix ls -l, Windows DIR — not standardized)
- **NLST**: filenames only
- **MLSD**: machine-readable RFC 3659 format (preferred)

## Testing Practices

- Protocol codec tests: encode/decode round-trip for all commands and replies
- Data connection tests: PORT/EPRT/PASV/EPSV argument formatting and parsing
- Transfer tests: ASCII CRLF conversion, binary pass-through
- Filesystem tests: chroot enforcement, in-memory operations
- Server tests: concurrent connections, command handlers, session state
- Client-server integration tests: full workflows with real TCP connections
- Demo functional tests: each demo runs against a local server
- All tests use loopback transport (no external FTP server required)
- Test count: 386
