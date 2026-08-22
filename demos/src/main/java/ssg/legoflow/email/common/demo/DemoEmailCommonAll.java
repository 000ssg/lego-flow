package ssg.legoflow.email.common.demo;

import ssg.legoflow.email.common.address.EmailAddress;
import ssg.legoflow.email.common.address.Mailbox;
import ssg.legoflow.email.common.builder.MimeMessageBuilder;
import ssg.legoflow.email.common.encoding.Base64Codec;
import ssg.legoflow.email.common.encoding.EncodedWordCodec;
import ssg.legoflow.email.common.encoding.QuotedPrintableCodec;
import ssg.legoflow.email.common.mime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/**
 * Comprehensive demo of all email/common (MIME) module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>MIME parsing — parse raw email into structured MimeMessage</li>
 *   <li>MIME writing — serialize MimeMessage back to bytes</li>
 *   <li>Round-trip — parse-write-parse preserves data</li>
 *   <li>Message builder — fluent construction of email messages</li>
 *   <li>Multipart messages — mixed and alternative structures</li>
 *   <li>Content encodings — Base64 and Quoted-Printable</li>
 *   <li>Encoded words — RFC 2047 header encoding/decoding</li>
 *   <li>Email addresses — parsing and validation</li>
 *   <li>Content type — parsing and inspection</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoEmailCommonAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoEmailCommonAll.class);

    /** Set to {@code true} to use external resources. */
    public static boolean USE_EXTERNAL = false;

    private DemoEmailCommonAll() {}

    /**
     * Results from running the full demo.
     *
     * @param mimeParsing         true if MIME parsing produced valid message
     * @param mimeWriting         true if MIME writing produced valid bytes
     * @param roundTrip           true if parse-write-parse preserves data
     * @param messageBuilder      true if builder produced valid message
     * @param multipartMessage    true if multipart construction and parsing worked
     * @param contentEncodings    true if Base64 and Quoted-Printable round-trip
     * @param encodedWords        true if RFC 2047 encode/decode round-trip
     * @param emailAddresses      true if email address parsing succeeded
     * @param contentTypeParsing  true if content type parsing succeeded
     * @since 0.1.0
     */
    public record Results(
            boolean mimeParsing,
            boolean mimeWriting,
            boolean roundTrip,
            boolean messageBuilder,
            boolean multipartMessage,
            boolean contentEncodings,
            boolean encodedWords,
            boolean emailAddresses,
            boolean contentTypeParsing
    ) {}

    /**
     * Runs the comprehensive demo covering all MIME features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean parsing = demoMimeParsing();
        boolean writing = demoMimeWriting();
        boolean roundTrip = demoRoundTrip();
        boolean builder = demoMessageBuilder();
        boolean multipart = demoMultipartMessage();
        boolean encodings = demoContentEncodings();
        boolean encodedWords = demoEncodedWords();
        boolean addresses = demoEmailAddresses();
        boolean contentType = demoContentTypeParsing();

        return new Results(
                parsing, writing, roundTrip, builder, multipart,
                encodings, encodedWords, addresses, contentType
        );
    }

    // ======================== 1. MIME PARSING =================================

    /**
     * Demonstrates parsing a raw email message into a structured MimeMessage.
     *
     * @since 0.1.0
     */
    static boolean demoMimeParsing() {
        LOG.info("=== 1. MIME Parsing ===");

        String raw = """
                From: Alice <alice@example.com>\r
                To: Bob <bob@example.com>\r
                Subject: Hello\r
                Date: Mon, 01 Jan 2024 12:00:00 +0000\r
                MIME-Version: 1.0\r
                Content-Type: text/plain; charset=UTF-8\r
                Content-Transfer-Encoding: 7bit\r
                \r
                Hello, Bob! This is a test message.\r
                """;

        var message = MimeParser.parse(raw);
        LOG.info("Subject: {}", message.subject());
        LOG.info("From: {}", message.from());
        LOG.info("To: {}", message.to());
        LOG.info("Is multipart: {}", message.isMultipart());
        LOG.info("Content-Type: {}", message.contentType().mediaType());

        boolean subjectOk = "Hello".equals(message.subject());
        boolean fromOk = !message.from().isEmpty()
                && "alice@example.com".equals(message.from().getFirst().address().address());
        boolean toOk = !message.to().isEmpty()
                && "bob@example.com".equals(message.to().getFirst().address().address());
        boolean notMultipart = !message.isMultipart();
        boolean hasBody = message.body() != null;

        return subjectOk && fromOk && toOk && notMultipart && hasBody;
    }

    // ======================== 2. MIME WRITING =================================

    /**
     * Demonstrates writing a MimeMessage to bytes.
     *
     * @since 0.1.0
     */
    static boolean demoMimeWriting() {
        LOG.info("=== 2. MIME Writing ===");

        var msg = MimeMessageBuilder.create()
                .from("alice@example.com")
                .to("bob@example.com")
                .subject("Test Write")
                .textBody("Hello from MIME writer!")
                .build();

        byte[] written = MimeWriter.write(msg);
        String output = new String(written, StandardCharsets.UTF_8);
        LOG.info("Written: {} bytes", written.length);
        LOG.info("Output preview: {}", output.substring(0, Math.min(200, output.length())));

        boolean hasFrom = output.contains("alice@example.com");
        boolean hasTo = output.contains("bob@example.com");
        boolean hasSubject = output.contains("Test Write");
        boolean hasBody = output.contains("Hello from MIME writer!");
        boolean nonEmpty = written.length > 0;

        return hasFrom && hasTo && hasSubject && hasBody && nonEmpty;
    }

    // ======================== 3. ROUND-TRIP ===================================

    /**
     * Demonstrates parse-write-parse round-trip preserving key data.
     *
     * @since 0.1.0
     */
    static boolean demoRoundTrip() {
        LOG.info("=== 3. Round-Trip ===");

        var original = MimeMessageBuilder.create()
                .from("Alice Sender", "alice@example.com")
                .to("Bob Receiver", "bob@example.com")
                .subject("Round-Trip Test")
                .textBody("This message should survive a round-trip.")
                .build();

        // Write to bytes
        byte[] written = MimeWriter.write(original);

        // Parse back
        var parsed = MimeParser.parse(written);

        LOG.info("Original subject: {}", original.subject());
        LOG.info("Parsed subject: {}", parsed.subject());

        boolean subjectOk = "Round-Trip Test".equals(parsed.subject());
        boolean fromOk = !parsed.from().isEmpty();
        boolean toOk = !parsed.to().isEmpty();
        boolean multipartSame = original.isMultipart() == parsed.isMultipart();

        return subjectOk && fromOk && toOk && multipartSame;
    }

    // ======================== 4. MESSAGE BUILDER ==============================

    /**
     * Demonstrates the fluent MimeMessageBuilder for constructing messages.
     *
     * @since 0.1.0
     */
    static boolean demoMessageBuilder() {
        LOG.info("=== 4. Message Builder ===");

        var msg = MimeMessageBuilder.create()
                .from("Alice", "alice@example.com")
                .to("Bob", "bob@example.com")
                .subject("Builder Demo")
                .textBody("Plain text content.")
                .build();

        LOG.info("Built message: subject={}, from={}, to={}",
                msg.subject(), msg.from(), msg.to());
        LOG.info("Content-Type: {}", msg.contentType().mediaType());
        LOG.info("Is multipart: {}", msg.isMultipart());

        boolean hasSubject = "Builder Demo".equals(msg.subject());
        boolean hasFrom = !msg.from().isEmpty();
        boolean hasTo = !msg.to().isEmpty();
        boolean hasContentType = msg.contentType() != null;

        return hasSubject && hasFrom && hasTo && hasContentType;
    }

    // ======================== 5. MULTIPART MESSAGE ============================

    /**
     * Demonstrates building and parsing multipart (mixed, alternative) messages.
     *
     * @since 0.1.0
     */
    static boolean demoMultipartMessage() {
        LOG.info("=== 5. Multipart Message ===");

        // Build multipart/mixed with text body and attachment
        var msg = MimeMessageBuilder.create()
                .from("alice@example.com")
                .to("bob@example.com")
                .subject("Multipart Test")
                .textBody("Plain text part.")
                .attachment("test.txt", "text/plain",
                        "Attachment content".getBytes(StandardCharsets.UTF_8))
                .build();

        LOG.info("Is multipart: {}", msg.isMultipart());
        LOG.info("Content-Type: {}", msg.contentType().mediaType());

        boolean isMultipart = msg.isMultipart();

        // Count all parts
        var allParts = msg.allParts();
        LOG.info("Total parts: {}", allParts.size());
        boolean hasParts = allParts.size() >= 2;

        // Round-trip the multipart message
        byte[] written = MimeWriter.write(msg);
        var parsed = MimeParser.parse(written);
        LOG.info("Parsed multipart: {}", parsed.isMultipart());
        boolean parsedMultipart = parsed.isMultipart();
        boolean parsedSubject = "Multipart Test".equals(parsed.subject());

        return isMultipart && hasParts && parsedMultipart && parsedSubject;
    }

    // ======================== 6. CONTENT ENCODINGS ============================

    /**
     * Demonstrates Base64 and Quoted-Printable encoding/decoding.
     *
     * @since 0.1.0
     */
    static boolean demoContentEncodings() {
        LOG.info("=== 6. Content Encodings ===");

        // Base64 round-trip
        byte[] original = "Hello, World! This is Base64 encoded.".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Codec.encode(original);
        byte[] decoded = Base64Codec.decode(encoded);
        LOG.info("Base64 encoded: {}", encoded.replaceAll("\\s+", "").substring(0, Math.min(40, encoded.length())));
        boolean base64Ok = Arrays.equals(original, decoded);
        LOG.info("Base64 round-trip: {}", base64Ok);

        // Raw Base64 (no line wrapping)
        String rawEncoded = Base64Codec.encodeRaw(original);
        byte[] rawDecoded = Base64Codec.decode(rawEncoded);
        boolean rawBase64Ok = Arrays.equals(original, rawDecoded);
        LOG.info("Raw Base64 round-trip: {}", rawBase64Ok);

        // Quoted-Printable round-trip
        byte[] qpOriginal = "Hello, Wörld! Special chars: =, >, <."
                .getBytes(StandardCharsets.UTF_8);
        String qpEncoded = QuotedPrintableCodec.encode(qpOriginal);
        byte[] qpDecoded = QuotedPrintableCodec.decode(qpEncoded);
        LOG.info("QP encoded: {}", qpEncoded.substring(0, Math.min(60, qpEncoded.length())));
        boolean qpOk = Arrays.equals(qpOriginal, qpDecoded);
        LOG.info("QP round-trip: {}", qpOk);

        return base64Ok && rawBase64Ok && qpOk;
    }

    // ======================== 7. ENCODED WORDS ================================

    /**
     * Demonstrates RFC 2047 encoded-word encoding and decoding for headers.
     *
     * @since 0.1.0
     */
    static boolean demoEncodedWords() {
        LOG.info("=== 7. Encoded Words ===");

        // Encode with Base64 method
        String text = "Héllo Wörld";
        String b64Encoded = EncodedWordCodec.encodeBase64(text, StandardCharsets.UTF_8);
        LOG.info("B64 encoded word: {}", b64Encoded);
        String b64Decoded = EncodedWordCodec.decode(b64Encoded);
        LOG.info("B64 decoded: {}", b64Decoded);
        boolean b64Ok = text.equals(b64Decoded);

        // Encode with Q method
        String qEncoded = EncodedWordCodec.encodeQ(text, StandardCharsets.UTF_8);
        LOG.info("Q encoded word: {}", qEncoded);
        String qDecoded = EncodedWordCodec.decode(qEncoded);
        LOG.info("Q decoded: {}", qDecoded);
        boolean qOk = text.equals(qDecoded);

        // Auto-encode (picks method based on content)
        String autoEncoded = EncodedWordCodec.encode(text);
        String autoDecoded = EncodedWordCodec.decode(autoEncoded);
        boolean autoOk = text.equals(autoDecoded);
        LOG.info("Auto encode/decode: {}", autoOk);

        // Pure ASCII can be encoded and decoded correctly
        String ascii = "Hello World";
        String asciiEncoded = EncodedWordCodec.encode(ascii);
        String asciiDecoded = EncodedWordCodec.decode(asciiEncoded);
        boolean asciiRoundTrip = ascii.equals(asciiDecoded);
        LOG.info("ASCII round-trip: {}", asciiRoundTrip);

        return b64Ok && qOk && autoOk && asciiRoundTrip;
    }

    // ======================== 8. EMAIL ADDRESSES ==============================

    /**
     * Demonstrates email address parsing and validation.
     *
     * @since 0.1.0
     */
    static boolean demoEmailAddresses() {
        LOG.info("=== 8. Email Addresses ===");

        // Simple email address
        var addr1 = EmailAddress.parse("alice@example.com");
        LOG.info("Address: local={}, domain={}", addr1.localPart(), addr1.domain());
        boolean addr1Ok = "alice".equals(addr1.localPart())
                && "example.com".equals(addr1.domain());

        // Mailbox with display name
        var mailbox = new Mailbox("Alice Sender", addr1);
        LOG.info("Mailbox: name={}, email={}", mailbox.displayName(), mailbox.address().address());
        boolean mailboxOk = "Alice Sender".equals(mailbox.displayName())
                && "alice@example.com".equals(mailbox.address().address());

        // Address without display name
        var mailbox2 = new Mailbox(addr1);
        boolean noDN = mailbox2.displayName() == null || mailbox2.displayName().isEmpty();
        LOG.info("Mailbox without display name: {}", noDN);

        // Complex local part
        var addr2 = EmailAddress.parse("user.name+tag@example.org");
        LOG.info("Complex: local={}, domain={}", addr2.localPart(), addr2.domain());
        boolean addr2Ok = "user.name+tag".equals(addr2.localPart())
                && "example.org".equals(addr2.domain());

        return addr1Ok && mailboxOk && noDN && addr2Ok;
    }

    // ======================== 9. CONTENT TYPE PARSING =========================

    /**
     * Demonstrates ContentType parsing and inspection.
     *
     * @since 0.1.0
     */
    static boolean demoContentTypeParsing() {
        LOG.info("=== 9. Content Type Parsing ===");

        // Parse text/plain with charset
        var ct1 = ContentType.parse("text/plain; charset=UTF-8");
        LOG.info("CT1: type={}, subtype={}, charset={}", ct1.type(), ct1.subtype(), ct1.charset());
        boolean ct1Ok = "text".equals(ct1.type()) && "plain".equals(ct1.subtype())
                && ct1.isText() && !ct1.isMultipart();

        // Parse multipart/mixed with boundary
        var ct2 = ContentType.parse("multipart/mixed; boundary=\"----=_Part_123\"");
        LOG.info("CT2: type={}, boundary={}", ct2.mediaType(), ct2.boundary());
        boolean ct2Ok = ct2.isMultipart() && ct2.boundary() != null
                && ct2.boundary().contains("Part_123");

        // Parse text/html
        var ct3 = ContentType.parse("text/html; charset=iso-8859-1");
        LOG.info("CT3: {}", ct3.mediaType());
        boolean ct3Ok = ct3.isText() && "html".equals(ct3.subtype());

        // Predefined constants
        boolean defaultOk = ContentType.DEFAULT.isText();
        boolean plainOk = "text/plain".equals(ContentType.TEXT_PLAIN.mediaType());
        boolean htmlOk = "text/html".equals(ContentType.TEXT_HTML.mediaType());
        LOG.info("Constants: DEFAULT={}, TEXT_PLAIN={}, TEXT_HTML={}",
                defaultOk, plainOk, htmlOk);

        return ct1Ok && ct2Ok && ct3Ok && defaultOk && plainOk && htmlOk;
    }
}
