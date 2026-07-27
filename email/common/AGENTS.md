# Email Common Module — Development Guide

## Module Purpose

The `email/common` module provides shared MIME parsing and construction facilities per RFC 2045-2049. It is used by both the SMTP and IMAP modules as the foundational layer for email message handling. All implementations are JDK-only with no external runtime dependencies.

## Key Classes

- `MimeParser` — parses raw bytes/strings into `MimeMessage` with recursive multipart support
- `MimeWriter` — serializes `MimeMessage` back to RFC 2045-compliant wire format
- `MimeMessage` — complete MIME message with headers, simple body, or multipart body
- `MimePart` — single MIME part with headers and raw/decoded content
- `MimeMultipart` — boundary-delimited multipart container with nested parts
- `MimeHeaders` — case-insensitive header map with folding/unfolding (RFC 5322)
- `ContentType` — Content-Type header parsing with media type and parameters (RFC 2045)
- `ContentDisposition` — Content-Disposition header parsing (RFC 2183)
- `ContentTransferEncoding` — enum for 7bit, 8bit, binary, quoted-printable, base64 (RFC 2045 section 6)
- `MultipartType` — enum for mixed, alternative, related, digest, report, signed subtypes
- `MimeMessageBuilder` / `MimePartBuilder` / `MultipartBuilder` — fluent builders

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `mime` | Core MIME types: message, part, multipart, headers, content-type, content-disposition, content-transfer-encoding, parser, writer |
| `encoding` | Base64 (RFC 2045), Quoted-Printable (RFC 2045), RFC 2047 encoded-word codec, charset utilities with BOM detection |
| `address` | RFC 5322 email address, mailbox (display name + address), address group, address list parser |
| `header` | Header fields, RFC 5322 date-time parser, Message-ID, RFC 2231 parameter parser with continuations |
| `builder` | Fluent builders for MIME messages, parts, and multipart containers |

## Module-Specific Coding Conventions

### Content-Transfer-Encoding Values
- `7bit` (default per RFC 2045), `8bit`, `binary`, `quoted-printable`, `base64`

### Multipart Subtypes
- `mixed` (independent parts), `alternative` (same content, different formats), `related` (root + linked parts), `digest`, `report`, `signed`

### MIME Message Structure
- Headers separated from body by blank line (CRLF CRLF)
- Multipart bodies delimited by `--boundary` / `--boundary--`
- Parts may contain nested multipart structures (recursive)
- Preamble (before first boundary) and epilogue (after closing boundary) are preserved

### Builder Patterns
- `MimeMessageBuilder.create()` — fluent builder for complete messages with from/to/subject/text/html/attachments
- `MimePartBuilder.create()` — fluent builder for individual parts with content type, disposition, encoding
- `MultipartBuilder.mixed()` / `.alternative()` / `.related()` — factory methods for multipart containers
- Builders auto-encode content (Base64, QP) when `.encodeContent()` is called

### Header Handling
- Case-insensitive lookup, insertion-order preservation
- RFC 5322 folding at 78 chars, hard limit at 998
- RFC 2047 encoded-word decoding in `HeaderField.decodedValue()`
- RFC 2231 parameter continuations and charset encoding in `ParameterParser`

### Address Parsing
- Handles: `user@example.com`, `<user@example.com>`, `"Name" <user@example.com>`, groups
- Domain comparison is case-insensitive per RFC 5321, local-part is case-sensitive
- RFC 2047 encoded display names are decoded automatically

### Charset Detection
- BOM detection (UTF-8, UTF-16 BE/LE)
- Common alias resolution (latin1, cp1252, utf8, etc.)
- Heuristic detection: BOM -> ASCII -> UTF-8 -> ISO-8859-1 fallback

## Testing Practices

- Unit tests for each class in corresponding test package
- Codec round-trip tests: encode -> decode for Base64, Quoted-Printable, encoded-word
- Parser tests: parse raw MIME -> verify structure, headers, content
- Writer tests: write message -> parse back -> verify equivalence
- Builder tests: fluent API produces correct MIME structure
- Address parser tests: all address formats including groups and encoded display names
- Date-time parser tests: RFC 5322 standard and common non-standard formats
- Parameter parser tests: RFC 2231 continuations and charset encoding
- All tests use JUnit 5 + AssertJ
- Test count: 260

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
