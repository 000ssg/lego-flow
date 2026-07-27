# Lego Flow Syslog

RFC 5424 structured syslog protocol implementation with UDP (RFC 5426), TCP (RFC 6587), and TLS (RFC 5425) transports.

## Features

- Full RFC 5424 message format: PRI, VERSION, TIMESTAMP, HOSTNAME, APP-NAME, PROCID, MSGID, STRUCTURED-DATA, MSG
- All 24 facility codes (kern through local7) and 8 severity levels (emergency through debug)
- Structured data with proper escaping and well-known SD-IDs (timeQuality, origin, meta)
- UDP transport: one message per datagram
- TCP transport: octet counting and non-transparent framing
- TLS transport: encrypted syslog over TLS with octet counting
- High-level SyslogSender and SyslogCollector APIs
- Virtual threads for concurrent connection handling

## Quick Start

### Sending Messages

```java
// UDP sender
try (var sender = SyslogSender.udp("syslog.example.com")) {
    sender.send(Facility.DAEMON, Severity.INFO, "Service started");
}

// TCP sender with structured data
try (var sender = SyslogSender.tcp("syslog.example.com", 514)
        .withHostname("myhost")
        .withAppName("myapp")) {
    var sd = StructuredData.builder("origin")
            .param("ip", "192.168.1.1")
            .build();
    sender.send(Facility.AUTH, Severity.WARNING, "Login failed", List.of(sd));
}
```

### Collecting Messages

```java
var collector = SyslogCollector.builder()
        .udp(514)
        .tcp(514)
        .build();
collector.start(msg -> {
    System.out.printf("[%s.%s] %s%n",
            msg.facility(), msg.severity(), msg.message());
});
```

## Architecture

```
ssg.legoflow.network.syslog
├── protocol/
│   ├── Facility          — 24 syslog facility codes
│   ├── Severity          — 8 severity levels
│   ├── StructuredData    — SD-ELEMENT with ID and params
│   ├── SyslogMessage     — Complete RFC 5424 message record
│   ├── SyslogCodec       — Encode/decode RFC 5424 format
│   └── SyslogParseException
├── transport/
│   ├── FramingMode       — OCTET_COUNTING, NON_TRANSPARENT
│   ├── UdpSender         — UDP datagram sender
│   ├── UdpCollector      — UDP datagram receiver
│   ├── TcpSender         — TCP stream sender
│   ├── TcpCollector      — TCP stream receiver
│   ├── TlsSender         — TLS encrypted sender
│   └── TlsCollector      — TLS encrypted receiver
├── SyslogSender          — High-level send API
└── SyslogCollector       — High-level collect API
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) — module design, Mermaid diagrams, transport model
- [Requirements](doc/REQUIREMENTS.md) — technical requirements and commit history
- [Compliance Matrix](doc/COMPLIANCE.md) — RFC 5424/5425/5426/6587 coverage with known limitations
- [Development Guide](CLAUDE.md) — coding conventions and testing practices for Claude
