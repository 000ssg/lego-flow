# FTP Module Requirements

## Initial Implementation

### Original Request
> Create the FTP module implementing FTP (RFC 959) and FTPS (RFC 4217) protocols from scratch. Both client and server implementations. SFTP is NOT part of this module.

### Reformulated Requirements
1. Protocol layer: enum of all FTP commands (RFC 959 + extensions), reply codes (110-553), transfer types (ASCII/BINARY/EBCDIC), structure (FILE/RECORD/PAGE), mode (STREAM/BLOCK/COMPRESSED), text-based codec (CRLF terminated)
2. Data connections: active mode (PORT/EPRT), passive mode (PASV/EPSV), data transfer with ASCII CRLF conversion and binary pass-through
3. FTPS security: TLS configuration (keystore, truststore), AUTH TLS/SSL, PBSZ/PROT commands, implicit (port 990) and explicit (port 21) modes
4. FTP client: connect/disconnect, login, directory operations, file transfers, listing parsers (Unix ls -l, Windows DIR, MLSD RFC 3659)
5. FTP server: virtual threads for concurrent connections, pluggable filesystem (local with chroot, in-memory), configurable authentication, session management
6. Demos: client, server, FTPS, file transfer, virtual filesystem
7. Tests: 150+ tests covering protocol, data, security, client, server, and integration

### Final Design Decisions
- Text-based protocol codec using BufferedReader/OutputStream for control channel
- Separate DataConnection interface with Active/Passive implementations
- FtpFileSystem interface allowing pluggable storage backends
- FtpAuthenticator as @FunctionalInterface for flexible authentication
- Builder pattern for all configuration classes
- Virtual threads for server connection handling
- In-memory filesystem for testing (no external FTP server required)
- MLSD parser following RFC 3659 machine-readable format exactly
- FtpListParser handling both Unix ls -l and Windows DIR formats

### Implementation Details
- 6 packages: protocol, data, security, client, server, demo
- 28 source files
- 16 test files
- 150+ individual tests

### Test Coverage
- Protocol: command parsing, reply encoding/decoding, codec round-trips
- Data: PORT/EPRT/PASV/EPSV formatting, ASCII/binary transfers
- Security: PBSZ/PROT handling, TLS config, SSL context creation
- Client: full integration tests against embedded server
- Server: connection handling, command processing, session management
- Filesystem: local (with chroot enforcement), in-memory operations
- Demos: all demos run as functional tests
- Integration: client-server workflows, concurrent clients, file integrity
