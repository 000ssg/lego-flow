# Interop Tests — Protocol Comparison

## Test Coverage Matrix

### SSH Interoperability

| Test | Reference | Protocol | Status |
|------|-----------|----------|--------|
| `SshServerInteropTest.testVersionExchangeWithOpenSSH` | OpenSSH sshd | RFC 4253 §4.2 | Requires: sshd running |
| `SshServerInteropTest.testOurClientVersionExchange` | OpenSSH sshd | RFC 4253 KEX | Requires: sshd + credentials |
| `SshServerInteropTest.testRawVersionStringFormat` | RFC 4253 §4.2 | SSH version banner | Always runs |
| `SshServerInteropTest.testOpenSSHVersionParsing` | OpenSSH | RFC 4253 §4.2 | Always runs |
| `SshServerInteropTest.testVersionIncompatibilityRejection` | RFC 4253 | SSH-1 rejection | Always runs |
| `SshServerInteropTest.testMalformedVersionStrings` | RFC 4253 §4.2 | Error handling | Always runs |
| `SshServerInteropTest.testOpenSSHCompatRoundTrip` | OpenSSH sshd | RFC 4253 | Requires: sshd running |

**Reference**: OpenSSH (https://www.openssh.com/), RFC 4251–4256
**Prerequisites**: OpenSSH sshd at `interop.sshd.port` (default 2222), valid credentials

### Telnet Interoperability

| Test | Reference | Protocol | Status |
|------|-----------|----------|--------|
| `TelnetServerInteropTest.testParserSubnegotiationParsing` | RFC 854 §3 | SB…SE framing | Always runs |
| `TelnetServerInteropTest.testParserIacEscaping` | RFC 854 §1 | IAC IAC → 0xFF | Always runs |
| `TelnetServerInteropTest.testConnectionIacEscaping` | RFC 854 §1 | Outbound escaping | Always runs |
| `TelnetServerInteropTest.testConnectionNegotiationCommands` | RFC 855 | WILL/WONT/DO/DONT | Always runs |
| `TelnetServerInteropTest.testConnectionSubnegotiation` | RFC 854 §3 | SB data SE | Always runs |
| `TelnetServerInteropTest.testTelnetCommandResolution` | RFC 854 | fromCode() | Always runs |
| `TelnetServerInteropTest.testTelnetOptionMapping` | RFC 855 | Option codes | Always runs |
| `TelnetServerInteropTest.testBinaryTranslationInbound` | RFC 856 | CR NL → LF | Always runs |
| `TelnetServerInteropTest.testBinaryTranslationOutbound` | RFC 856 | LF → CR NL | Always runs |
| `TelnetServerInteropTest.testBinaryModeBypassesTranslation` | RFC 856 | Binary mode off | Always runs |
| `TelnetServerInteropTest.testNewEnvDefaultEnvironment` | RFC 1408 | TERM/COLS/LINES | Always runs |
| `TelnetServerInteropTest.testNewEnvInfoRequest` | RFC 1408 | INFO suboption | Always runs |
| `TelnetServerInteropTest.testNewEnvSetAndGet` | RFC 1408 | Variable storage | Always runs |
| `TelnetServerInteropTest.testTTYPEHandlerSendResponse` | RFC 1091 | SEND → IS | Always runs |
| `TelnetServerInteropTest.testNAWSHandlerParsesWindow` | RFC 1073 | 4-byte size | Always runs |
| `TelnetServerInteropTest.testLinemodeLineSubmission` | RFC 1143 | CR → line | Always runs |
| `TelnetServerInteropTest.testRawTelnetEchoProtocol` | RFC 854 | Full telnet | Requires: netcat -t |

**References**:
- OpenBSD telnet client (RFC 854–856)
- Netcat with `-t` flag (telnet negotiation mode)
- RFC 856 Binary Mode
- RFC 1091 TTYPE
- RFC 1073 NAWS
- RFC 1143 LINEMODE
- RFC 1408 NEW_ENV

**Prerequisites**: Netcat with telnet mode (`nc -t`) at `interop.telnet.port` (default 2223)

### Terminal Interoperability

| Test | Reference | Protocol | Status |
|------|-----------|----------|--------|
| `TerminalEmulatorInteropTest.testCursorUp` | DEC VT100 User Manual §6-8 | CSI n A | Always runs |
| `TerminalEmulatorInteropTest.testCursorDown` | DEC VT100 User Manual §6-8 | CSI n B | Always runs |
| `TerminalEmulatorInteropTest.testCursorForward` | DEC VT100 User Manual §6-8 | CSI n C | Always runs |
| `TerminalEmulatorInteropTest.testCursorBack` | DEC VT100 User Manual §6-8 | CSI n D | Always runs |
| `TerminalEmulatorInteropTest.testCursorPosition` | DEC VT100 User Manual §6-8 | CSI r;c H | Always runs |
| `TerminalEmulatorInteropTest.testCursorHorizontalAbsolute` | DEC VT100 User Manual §6-8 | CSI n G | Always runs |
| `TerminalEmulatorInteropTest.testBoldOn` | ECMA-48 §9.3.5 | SGR 1 | Always runs |
| `TerminalEmulatorInteropTest.testUnderlineOn` | ECMA-48 §9.3.5 | SGR 4 | Always runs |
| `TerminalEmulatorInteropTest.testForegroundColor` | ECMA-48 §9.3.5 | SGR 30–37 | Always runs |
| `TerminalEmulatorInteropTest.testAllStdioColors` | ECMA-48 §9.3.5 | SGR 30–37 | Always runs |
| `TerminalEmulatorInteropTest.testVideoReverse` | ECMA-48 §9.3.5 | SGR 52 | Always runs |
| `TerminalEmulatorInteropTest.testVideoNormal` | ECMA-48 §9.3.5 | SGR 55 | Always runs |
| `TerminalEmulatorInteropTest.testReset` | VT100 Manual | RSTR | Always runs |
| `TerminalEmulatorInteropTest.testRenderReturnsCorrectLines` | DEC VT100 | Screen buffer | Always runs |
| `TerminalEmulatorInteropTest.testEraseEntireScreen` | VT100 Manual | CSI 2J | Always runs |
| `TerminalEmulatorInteropTest.testEraseLineEntire` | VT100 Manual | CSI 2K | Always runs |
| `TerminalEmulatorInteropTest.testInsertLine` | VT100 Manual | CSI n L | Always runs |
| `TerminalEmulatorInteropTest.testDeleteLine` | VT100 Manual | CSI n M | Always runs |
| `TerminalEmulatorInteropTest.testXtermDecPrivateModes` | xterm ctlseqs | DECAWM, DECORM | Always runs |
| `TerminalEmulatorInteropTest.testXterm256Color` | xterm ctlseqs | CSI 38;5;n m | Always runs |
| `TerminalEmulatorInteropTest.testXtermTrueColor` | xterm ctlseqs | CSI 38;2;r;g;b m | Always runs |
| `TerminalEmulatorInteropTest.testReferenceEscapeSequenceFormats` | DEC VT100 Manual | Well-known sequences | Always runs |
| `TerminalEmulatorInteropTest.testDsrResponses` | DEC VT100 | CSI 6n | Always runs |
| `TerminalEmulatorInteropTest.testDecrqmResponses` | DEC VT100 | CSI $?p | Always runs |

**References**:
- DEC VT100 User Manual (1982)
- xterm Control Sequence (ctlseqs) documentation
- ECMA-48 (ISO/IEC 6429)
- ISO/IEC 2022 (character set encoding)

### Comparison with Other Libraries

| Aspect | lego-flow interop | Apache MINA SSHD | Netty Telnet |
|--------|------------------|-----------------|--------------|
| SSH version exchange | ✅ Tests OpenSSH | ✅ Tests OpenSSH | N/A |
| SSH KEX negotiation | ✅ Tests with sshd | ✅ Tests with sshd | N/A |
| Telnet subnegotiation | ✅ Tests with Netcat | ❌ No telnet module | ✅ Tests with Netcat |
| Telnet IAC escaping | ✅ Verified | N/A | ❌ Not tested |
| Terminal CSI parsing | ✅ Against DEC VT100 spec | N/A | N/A |
| Terminal color support | ✅ 256-color, true color | N/A | N/A |
| Terminal DEC modes | ✅ DECSET/DECRST | N/A | N/A |
| Terminal DECRQM | ✅ Mode queries | N/A | N/A |

**Key Differentiator**: lego-flow is the only Java library that includes **both**
telnet protocol testing **and** terminal emulation verification in its interop test suite.
Most other projects test only the transport layer (SSH/Telnet) without validating
the terminal escape sequence parsing that sits behind the protocol.

---

**Last Updated**: 2026-08-18
