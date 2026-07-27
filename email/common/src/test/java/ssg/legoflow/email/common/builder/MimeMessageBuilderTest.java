package ssg.legoflow.email.common.builder;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.common.header.MessageId;
import ssg.legoflow.email.common.mime.*;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MimeMessageBuilder}.
 */
class MimeMessageBuilderTest {

    @Test
    void testBuildSimpleTextMessage() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("sender@example.com")
                .to("recipient@example.com")
                .subject("Hello")
                .textBody("Hello, World!")
                .build();

        assertThat(msg.headers().get("From")).isEqualTo("sender@example.com");
        assertThat(msg.headers().get("To")).isEqualTo("recipient@example.com");
        assertThat(msg.subject()).isEqualTo("Hello");
        assertThat(msg.headers().get("MIME-Version")).isEqualTo("1.0");
    }

    @Test
    void testBuildWithDisplayNames() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("John Doe", "john@example.com")
                .to("Jane Smith", "jane@example.com")
                .subject("Test")
                .textBody("Body")
                .build();

        assertThat(msg.headers().get("From")).contains("John Doe");
        assertThat(msg.headers().get("From")).contains("john@example.com");
    }

    @Test
    void testBuildWithDate() {
        OffsetDateTime date = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .date(date)
                .subject("Test")
                .textBody("Body")
                .build();

        assertThat(msg.headers().get("Date")).contains("15 Jan 2024");
    }

    @Test
    void testBuildWithMessageId() {
        MessageId id = new MessageId("unique@example.com");
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .messageId(id)
                .subject("Test")
                .textBody("Body")
                .build();

        assertThat(msg.headers().get("Message-ID")).isEqualTo("<unique@example.com>");
    }

    @Test
    void testBuildWithCcAndBcc() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("from@test.com")
                .to("to@test.com")
                .cc("cc@test.com")
                .bcc("bcc@test.com")
                .subject("Test")
                .textBody("Body")
                .build();

        assertThat(msg.headers().get("Cc")).isEqualTo("cc@test.com");
        assertThat(msg.headers().get("Bcc")).isEqualTo("bcc@test.com");
    }

    @Test
    void testBuildTextAndHtmlAlternative() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Alt")
                .textBody("Plain text")
                .htmlBody("<html><body>HTML</body></html>")
                .build();

        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.allParts()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testBuildWithAttachment() {
        byte[] pdfData = "fake-pdf-content".getBytes(StandardCharsets.UTF_8);
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("With Attachment")
                .textBody("See attached.")
                .attachment("report.pdf", "application/pdf", pdfData)
                .build();

        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.allParts().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testBuildWithMultipleAttachments() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Multiple Attachments")
                .textBody("See attached.")
                .attachment("file1.txt", "text/plain", "Content 1".getBytes())
                .attachment("file2.txt", "text/plain", "Content 2".getBytes())
                .build();

        assertThat(msg.isMultipart()).isTrue();
        assertThat(msg.allParts().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testBuildWithCustomHeader() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Custom")
                .header("X-Priority", "1")
                .textBody("Urgent!")
                .build();

        assertThat(msg.headers().get("X-Priority")).isEqualTo("1");
    }

    @Test
    void testBuildEmptyMessage() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Empty")
                .build();

        assertThat(msg.headers().get("Content-Type")).contains("text/plain");
    }

    @Test
    void testBuildWithExplicitMultipart() {
        MultipartBuilder mp = MultipartBuilder.mixed();
        mp.addPart(MimePartBuilder.create()
                .textPlain(StandardCharsets.UTF_8)
                .content("Hello")
                .build());

        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Explicit")
                .multipartBody(mp.build())
                .build();

        assertThat(msg.isMultipart()).isTrue();
    }

    @Test
    void testBuildWithNonAsciiSubject() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("to@test.com")
                .subject("Héllo Wörld")
                .textBody("Body")
                .build();

        String subject = msg.headers().get("Subject");
        assertThat(subject).contains("=?");
    }

    @Test
    void testBuildMultipleToRecipients() {
        MimeMessage msg = MimeMessageBuilder.create()
                .from("test@test.com")
                .to("alice@test.com")
                .to("bob@test.com")
                .subject("Group")
                .textBody("Hello all")
                .build();

        assertThat(msg.headers().get("To")).contains("alice@test.com");
        assertThat(msg.headers().get("To")).contains("bob@test.com");
    }
}
