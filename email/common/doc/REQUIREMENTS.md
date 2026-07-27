# Email Common Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 260
- **Dependencies**: blocks (DP/DF), slf4j-api (logging)
- **Standards**: RFC 2045, RFC 2046, RFC 2047, RFC 2048, RFC 2049, RFC 2183, RFC 2231, RFC 5322

---

## Requirements

### MIME Message Parsing (RFC 2045)
1. Parse raw bytes or strings into structured `MimeMessage` objects
2. Split headers from body at the blank line separator (CRLF CRLF or LF LF)
3. Parse header fields into name-value pairs with case-insensitive name lookup
4. Handle header folding (long lines wrapped with CRLF + whitespace) and unfolding
5. Support multiple headers with the same name (e.g., multiple Received headers)
6. Detect Content-Type to determine body structure (simple vs multipart)
7. Handle malformed input leniently where practical (bare LF, missing boundaries)

### Multipart Bodies (RFC 2046)
1. Parse multipart bodies by splitting on `--boundary` delimiters
2. Support recursive nesting (multipart containing multipart)
3. Recognize closing delimiter `--boundary--`
4. Preserve preamble text (before first boundary) and epilogue text (after closing boundary)
5. Support all standard multipart subtypes: mixed, alternative, related, digest, report, signed
6. Default Content-Type for digest parts is message/rfc822 per RFC 2046 section 5.1.5
7. Generate unique boundary strings for message construction

### Content-Type (RFC 2045 section 5)
1. Parse media type as `type/subtype` (case-insensitive)
2. Parse parameters: charset, boundary, name, and arbitrary key=value pairs
3. Support quoted parameter values with escape sequences
4. Default to `text/plain; charset=us-ascii` when Content-Type is absent (RFC 2045 section 5.2)
5. Provide convenience accessors for common checks: `isText()`, `isMultipart()`, `isMessage()`
6. Serialize back to header value format

### Content-Transfer-Encoding (RFC 2045 section 6)
1. Support all five encoding types: 7bit, 8bit, binary, quoted-printable, base64
2. Default to 7bit when header is absent
3. Parse encoding values case-insensitively
4. Reject unknown encoding values with clear error messages

### Base64 Encoding (RFC 2045 section 6.8)
1. Encode bytes to MIME Base64 with line wrapping at 76 characters
2. Use CRLF line endings between wrapped lines
3. Decode MIME Base64 leniently, ignoring whitespace
4. Support raw (unwrapped) Base64 encoding for non-MIME uses

### Quoted-Printable Encoding (RFC 2045 section 6.7)
1. Encode non-printable and non-ASCII bytes as `=XX` hex sequences
2. Pass through printable ASCII (33-126) except `=` which is always encoded
3. Pass through TAB and space except trailing whitespace before line breaks
4. Insert soft line breaks (`=CRLF`) to keep lines within 76 characters
5. Decode `=XX` sequences back to bytes
6. Handle soft line breaks (both `=CRLF` and `=LF` leniently)

### RFC 2047 Encoded Words
1. Encode non-ASCII text for use in headers using `=?charset?encoding?text?=` format
2. Support B (Base64) and Q (Q-encoding) methods
3. Decode encoded words in header values, resolving charset
4. Ignore whitespace between adjacent encoded words per RFC 2047 section 6.2
5. Check whether text needs encoding via `needsEncoding()`

### RFC 2231 Parameter Continuations
1. Assemble parameter continuations: `name*0=part1; name*1=part2`
2. Decode charset-encoded parameters: `name*=charset'language'value`
3. Handle combined continuation + charset encoding
4. Percent-decode parameter values

### Content-Disposition (RFC 2183)
1. Parse disposition type: inline, attachment, or custom
2. Parse parameters: filename, size, creation-date, modification-date
3. Provide convenience factory methods: `inline()`, `attachment(filename)`
4. Serialize back to header value format

### Address Parsing (RFC 5322)
1. Parse simple addresses: `user@example.com`
2. Parse angle-bracket addresses: `<user@example.com>`
3. Parse display name addresses: `"Name" <user@example.com>`
4. Parse unquoted display names: `Name <user@example.com>`
5. Parse named groups: `group-name: addr1, addr2;`
6. Parse comma-separated address lists
7. Handle quoted strings and escaped characters in display names
8. Decode RFC 2047 encoded display names

### Date-Time Parsing (RFC 5322)
1. Parse standard RFC 5322 date format: `Thu, 13 Feb 2020 15:30:00 +0000`
2. Handle optional day-of-week prefix
3. Handle optional seconds field
4. Support two-digit years
5. Support named timezone abbreviations (EST, PST, GMT, UTC, etc.)
6. Handle comments in date strings (parenthesized text)
7. Format dates to RFC 5322 format for message construction

### Message-ID (RFC 5322)
1. Parse message IDs in angle bracket format: `<unique@domain>`
2. Parse message ID lists for References and In-Reply-To headers
3. Generate unique message IDs using UUID
4. Serialize message ID lists for header output

### Charset Utilities
1. Resolve charset names with common alias support (utf8, latin1, cp1252, etc.)
2. Detect charset from byte order mark (UTF-8, UTF-16 BE/LE)
3. Validate UTF-8 byte sequences
4. Detect ASCII-only content
5. Heuristic charset detection: BOM -> ASCII -> UTF-8 -> ISO-8859-1 fallback
6. Convert between charsets with UTF-8 as target

### MIME Writing
1. Serialize `MimeMessage` to RFC 2045-compliant byte array
2. Write headers in wire format with CRLF line endings
3. Fold long header lines at word boundaries (78-char recommended, 998-char hard limit)
4. Write multipart bodies with proper boundary delimiters
5. Include preamble and epilogue when present
6. Handle nested multipart structures recursively
7. Encode content according to Content-Transfer-Encoding

### Fluent Builders
1. `MimeMessageBuilder`: construct messages with from/to/cc/bcc/subject/date/messageId/text/html/attachments
2. `MimePartBuilder`: construct parts with content-type/disposition/encoding/content
3. `MultipartBuilder`: construct multipart containers with typed subtypes and nested parts
4. Auto-select message structure based on provided content (text-only, html-only, alternative, mixed, related)
5. Auto-encode content (Base64, Quoted-Printable) when requested
6. Encode non-ASCII subjects as RFC 2047 encoded words
7. Set MIME-Version: 1.0 header automatically

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../../README.md) | [Root Architecture](../../../doc/ARCHITECTURE.md)
