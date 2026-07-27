# Email Common Module — Architecture

This document describes the architectural decisions for the email/common MIME parsing module.

---

## Module Purpose

The email/common module is the shared foundation for all email protocol modules (SMTP, IMAP). It provides complete MIME message parsing, writing, and construction per RFC 2045-2049, with no external dependencies beyond the JDK.

## Layered Architecture

```mermaid
graph TD
    L1["Fluent Builders<br/>(MimeMessageBuilder, MimePartBuilder, MultipartBuilder)"]
    L2["MIME Message Model<br/>(MimeMessage, MimePart, MimeMultipart, MimeHeaders)"]
    L3["Parser / Writer<br/>(MimeParser: bytes→model | MimeWriter: model→bytes)"]
    L4["Content Headers<br/>(ContentType, ContentDisposition, ContentTransferEncoding, MultipartType)"]
    L5["Encoding Layer<br/>(Base64Codec, QuotedPrintableCodec, EncodedWordCodec, CharsetUtils)"]
    L6["Header Utilities<br/>(HeaderField, ParameterParser, DateTimeParser, MessageId)"]
    L7["Address Parsing<br/>(EmailAddress, Mailbox, AddressGroup, AddressParser)"]

    L1 --> L2 --> L3 --> L4 --> L5
    L3 --> L6
    L2 --> L7
    L6 --> L5
    L7 --> L5
```

## Package Architecture

```mermaid
graph LR
    subgraph mime["mime package"]
        MimeParser
        MimeWriter
        MimeMessage
        MimePart
        MimeMultipart
        MimeHeaders
        ContentType
        ContentDisposition
        CTE["ContentTransferEncoding"]
        MultipartType
    end

    subgraph encoding["encoding package"]
        Base64Codec
        QPCodec["QuotedPrintableCodec"]
        EWCodec["EncodedWordCodec"]
        CharsetUtils
    end

    subgraph address["address package"]
        EmailAddress
        Mailbox
        AddressGroup
        AddressParser
    end

    subgraph header["header package"]
        HeaderField
        ParameterParser
        DateTimeParser
        MessageId
    end

    subgraph builder["builder package"]
        MimeMessageBuilder
        MimePartBuilder
        MultipartBuilder
    end

    MimeParser --> MimeMessage
    MimeParser --> MimeMultipart
    MimeParser --> MimePart
    MimeParser --> MimeHeaders
    MimeWriter --> MimeMessage
    MimeWriter --> Base64Codec
    MimeWriter --> QPCodec
    MimePart --> Base64Codec
    MimePart --> QPCodec
    MimeHeaders --> HeaderField
    MimeHeaders --> ContentType
    MimeHeaders --> ContentDisposition
    ContentType --> ParameterParser
    ContentDisposition --> ParameterParser
    HeaderField --> EWCodec
    EWCodec --> CharsetUtils
    AddressParser --> EWCodec
    Mailbox --> EWCodec
    MimeMessage --> AddressParser
    MimeMessage --> DateTimeParser
    MimeMessage --> MessageId
    MimeMessageBuilder --> MimeMessage
    MimePartBuilder --> MimePart
    MultipartBuilder --> MimeMultipart
```

## MIME Message Model

A MIME message has two structural forms:

```mermaid
graph TD
    MSG["MimeMessage"]
    HDR["MimeHeaders<br/>(Subject, From, To, Date, Content-Type, ...)"]
    BODY_SIMPLE["MimePart<br/>(simple body: text, binary)"]
    BODY_MULTI["MimeMultipart<br/>(boundary-delimited container)"]
    PART1["MimePart<br/>(text/plain)"]
    PART2["MimePart<br/>(text/html)"]
    NESTED["MimeMultipart<br/>(nested multipart/related)"]
    PART3["MimePart<br/>(text/html)"]
    PART4["MimePart<br/>(image/png, inline)"]

    MSG --> HDR
    MSG -->|"simple"| BODY_SIMPLE
    MSG -->|"multipart"| BODY_MULTI
    BODY_MULTI --> PART1
    BODY_MULTI --> PART2
    BODY_MULTI --> NESTED
    NESTED --> PART3
    NESTED --> PART4
```

- **Simple message**: `MimeMessage` -> `MimeHeaders` + `MimePart` (body)
- **Multipart message**: `MimeMessage` -> `MimeHeaders` + `MimeMultipart` (parts list)
- **Nested multipart**: `MimeMultipart` can contain other `MimeMultipart` instances for structures like `multipart/mixed` containing `multipart/alternative`

## Parsing Flow

```mermaid
sequenceDiagram
    participant Caller
    participant MimeParser
    participant MimeHeaders
    participant ContentType
    participant MimeMultipart

    Caller->>MimeParser: parse(bytes)
    MimeParser->>MimeParser: findHeaderEnd() (blank line)
    MimeParser->>MimeHeaders: parse(headerBlock)
    MimeHeaders->>ContentType: parse(Content-Type value)
    alt Content-Type is multipart
        MimeParser->>MimeParser: parseMultipart(body, boundary, subtype)
        loop Each boundary-delimited segment
            MimeParser->>MimeParser: parsePart(partText)
            Note right of MimeParser: Recursive: parts may<br/>themselves be multipart
        end
        MimeParser->>Caller: MimeMessage(headers, MimeMultipart)
    else Simple body
        MimeParser->>Caller: MimeMessage(headers, MimePart)
    end
```

## Writing Flow

```mermaid
sequenceDiagram
    participant Caller
    participant MimeWriter
    participant MimeHeaders
    participant Base64Codec
    participant QPCodec

    Caller->>MimeWriter: write(message)
    MimeWriter->>MimeHeaders: toWireFormat()
    MimeWriter->>MimeWriter: write CRLF (header/body separator)
    alt Multipart body
        MimeWriter->>MimeWriter: writeMultipart(multipart)
        Note right of MimeWriter: Write preamble, each part<br/>with --boundary delimiter,<br/>closing --boundary--, epilogue
    else Simple body
        MimeWriter->>Caller: write raw content bytes
    end
```

## Content-Transfer-Encoding

The encoding layer handles the transformation between raw content and wire-safe representations:

| Encoding | RFC 2045 Section | Line Limit | Use Case |
|----------|-----------------|------------|----------|
| 7bit (default) | 6.2 | 998 chars | ASCII-only text |
| 8bit | 6.3 | 998 chars | 8-bit text (requires 8BITMIME) |
| binary | 6.4 | None | Arbitrary binary (requires BINARYMIME) |
| quoted-printable | 6.7 | 76 chars | Mostly-ASCII text with some non-ASCII |
| base64 | 6.8 | 76 chars | Binary data (attachments, images) |

## Builder Architecture

The builder layer provides a fluent API for constructing MIME messages. `MimeMessageBuilder` automatically selects the appropriate message structure:

```mermaid
graph TD
    B["MimeMessageBuilder"]
    TEXT_ONLY["text/plain<br/>(simple body)"]
    HTML_ONLY["text/html<br/>(simple body)"]
    ALT["multipart/alternative<br/>(text + html)"]
    MIXED["multipart/mixed<br/>(content + attachments)"]
    REL["multipart/related<br/>(html + inline images)"]

    B -->|"textBody only"| TEXT_ONLY
    B -->|"htmlBody only"| HTML_ONLY
    B -->|"textBody + htmlBody"| ALT
    B -->|"any + attachments"| MIXED
    B -->|"htmlBody + inlineParts"| REL
```

The builder automatically nests structures when needed. For example, a message with text + HTML + attachments produces:

```
multipart/mixed
  multipart/alternative
    text/plain
    text/html
  application/pdf (attachment)
```

## Address Model

```mermaid
graph TD
    ADDR["EmailAddress<br/>(local-part @ domain)"]
    MBX["Mailbox<br/>(displayName? + EmailAddress)"]
    GRP["AddressGroup<br/>(name + List of Mailbox)"]
    PARSER["AddressParser<br/>(comma-separated, quoted, groups)"]

    PARSER --> MBX
    PARSER --> GRP
    MBX --> ADDR
    GRP --> MBX
```

- `EmailAddress`: `local-part@domain` with case-sensitive local-part and case-insensitive domain
- `Mailbox`: optional display name + email address, with RFC 2047 encoded display name support
- `AddressGroup`: named group of mailboxes (`group-name: addr1, addr2;`)
- `AddressParser`: handles all RFC 5322 address formats including nested quotes and angle brackets

## Header Processing

Headers use a two-layer model:

1. **Raw layer**: `HeaderField` stores name + raw value. `MimeHeaders` provides case-insensitive lookup with insertion-order preservation and support for duplicate names (e.g., multiple `Received` headers).
2. **Decoded layer**: `HeaderField.decodedValue()` applies RFC 2047 encoded-word decoding. Structured headers like `Content-Type` are parsed into domain objects by `ContentType.parse()`, `ContentDisposition.parse()`, etc.

Header folding (RFC 5322 section 2.1.1): lines exceeding 78 characters are folded at word boundaries with `CRLF + space`. Unfolding reverses this by removing `CRLF` followed by whitespace.

## RFC 2231 Parameter Handling

The `ParameterParser` supports RFC 2231 extensions for structured header parameters:

- **Continuations**: `filename*0="part1"; filename*1="part2"` assembled in order
- **Charset encoding**: `filename*=utf-8''%C3%A9tude.pdf` decoded with percent-encoding
- **Combined**: `filename*0*=utf-8''%C3%A9tude; filename*1*=.pdf`

This is critical for supporting non-ASCII filenames in `Content-Disposition` headers.

## Design Decisions

1. **Utility-class pattern**: `MimeParser`, `MimeWriter`, `AddressParser` are final classes with private constructors and static methods. No instances, no state.
2. **Immutable value objects**: `ContentType`, `ContentDisposition`, `EmailAddress`, `Mailbox`, `AddressGroup`, `MessageId`, `HeaderField` are all immutable.
3. **MimeHeaders is mutable**: allows incremental construction during parsing and building, but returns unmodifiable views from `fields()`.
4. **Raw content storage**: `MimePart` stores content in its encoded form. `decodedContent()` decodes on demand. This preserves the original encoding and avoids unnecessary decode/re-encode cycles.
5. **Lenient parsing**: the parser handles both CRLF and LF line endings, missing closing boundaries, and various address format deviations. Strict where required by the RFCs, lenient where real-world email varies.
6. **No external dependencies**: all encoding, parsing, and charset handling is implemented using JDK APIs only (`java.util.Base64`, `java.nio.charset`, `java.time`).

## Thread Safety

- All value objects (`ContentType`, `EmailAddress`, `Mailbox`, etc.) are immutable and thread-safe.
- `MimeParser` and `MimeWriter` are stateless utility classes -- thread-safe by design.
- `MimeHeaders` is mutable and not synchronized -- intended for single-threaded use during construction/parsing.
- Builders are single-threaded by design (create, configure, build, discard).

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../doc/ARCHITECTURE.md) | [Root README](../../../README.md)

---

**Last Updated**: 2026-07-06
