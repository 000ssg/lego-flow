
# Lego Flow Email Common Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-260-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

Shared MIME parsing library (RFC 2045-2049) for the Lego Flow email modules. Provides complete MIME message parsing, writing, and construction facilities used by both SMTP and IMAP.

## Overview

This module implements the MIME specification family (RFC 2045-2049) for encoding, structuring, and transporting email content. It serves as the shared foundation for all email protocol modules, handling everything from simple text messages to deeply nested multipart structures with attachments.

```
MimeMessageBuilder (fluent construction API)
  -> MimeMessage (headers + body or multipart)
    -> MimeHeaders (case-insensitive, folding/unfolding)
    -> MimePart (headers + encoded content)
    -> MimeMultipart (boundary-delimited container, recursive)
      -> ContentType / ContentDisposition / ContentTransferEncoding
        -> Encoding layer (Base64, Quoted-Printable, RFC 2047 encoded-word)
          -> Address parsing (RFC 5322 mailbox, group, address list)
```

## Features

- **MIME parsing** -- parse raw bytes or strings into structured `MimeMessage` with recursive multipart support
- **MIME writing** -- serialize messages back to RFC 2045-compliant wire format with CRLF line endings
- **Content-Type** -- full media type parsing with parameters (charset, boundary, name)
- **Content-Disposition** -- inline/attachment parsing with filename and size parameters
- **Content-Transfer-Encoding** -- 7bit, 8bit, binary, quoted-printable, base64
- **Multipart containers** -- mixed, alternative, related, digest, report, signed subtypes
- **Base64 codec** -- MIME Base64 with 76-character line wrapping (RFC 2045 section 6.8)
- **Quoted-Printable codec** -- encoding with soft line breaks (RFC 2045 section 6.7)
- **RFC 2047 encoded-word** -- non-ASCII text in headers (B and Q encodings)
- **RFC 2231 parameters** -- continuation and charset-encoded parameter values
- **Address parsing** -- RFC 5322 mailbox, display name, group, comma-separated lists
- **Date-time parsing** -- RFC 5322 dates with support for common non-standard formats
- **Message-ID** -- parsing, generation, and serialization for threading headers
- **Charset utilities** -- BOM detection, alias resolution, heuristic charset detection
- **Fluent builders** -- `MimeMessageBuilder`, `MimePartBuilder`, `MultipartBuilder`

## Quick Start

### Parse a MIME message

```java
byte[] rawMessage = ...; // from SMTP or IMAP
MimeMessage message = MimeParser.parse(rawMessage);

String subject = message.subject();
List<Mailbox> from = message.from();
OffsetDateTime date = message.date();

if (message.isMultipart()) {
    for (MimePart part : message.allParts()) {
        if (part.isText()) {
            String text = part.decodedContentAsString();
        } else if (part.isAttachment()) {
            String filename = part.filename();
            byte[] data = part.decodedContent();
        }
    }
}
```

### Build a message with attachments

```java
MimeMessage message = MimeMessageBuilder.create()
    .from("Alice", "alice@example.com")
    .to("Bob", "bob@example.com")
    .subject("Monthly Report")
    .date(OffsetDateTime.now())
    .messageId(MessageId.generate("example.com"))
    .textBody("Please find the report attached.")
    .htmlBody("<p>Please find the report attached.</p>")
    .attachment("report.pdf", "application/pdf", pdfBytes)
    .build();

byte[] wireFormat = MimeWriter.write(message);
```

### Build a multipart/alternative message

```java
MimePart textPart = MimePartBuilder.create()
    .textPlain(StandardCharsets.UTF_8)
    .transferEncoding(ContentTransferEncoding.QUOTED_PRINTABLE)
    .content("Hello, world!")
    .encodeContent()
    .build();

MimePart htmlPart = MimePartBuilder.create()
    .textHtml(StandardCharsets.UTF_8)
    .transferEncoding(ContentTransferEncoding.QUOTED_PRINTABLE)
    .content("<h1>Hello, world!</h1>")
    .encodeContent()
    .build();

MimeMultipart multipart = MultipartBuilder.alternative()
    .addPart(textPart)
    .addPart(htmlPart)
    .build();
```

### Parse addresses

```java
Mailbox mailbox = AddressParser.parseMailbox("\"John Doe\" <john@example.com>");
// mailbox.displayName() -> "John Doe"
// mailbox.address().address() -> "john@example.com"

List<Mailbox> recipients = AddressParser.parseMailboxList(
    "alice@example.com, \"Bob\" <bob@example.com>");
```

## Package Structure

```
ssg.legoflow.email.common/
├── mime/              -- MimeMessage, MimePart, MimeMultipart, MimeHeaders, MimeParser,
|                         MimeWriter, ContentType, ContentDisposition,
|                         ContentTransferEncoding, MultipartType
├── encoding/          -- Base64Codec, QuotedPrintableCodec, EncodedWordCodec, CharsetUtils
├── address/           -- EmailAddress, Mailbox, AddressGroup, AddressParser
├── header/            -- HeaderField, DateTimeParser, MessageId, ParameterParser
└── builder/           -- MimeMessageBuilder, MimePartBuilder, MultipartBuilder
```

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `slf4j-api` -- logging facade

No other external runtime dependencies. All MIME parsing and encoding is implemented from scratch using JDK APIs only.

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
