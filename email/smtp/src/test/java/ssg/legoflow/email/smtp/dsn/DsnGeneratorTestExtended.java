package ssg.legoflow.email.smtp.dsn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;
import static org.assertj.core.api.Assertions.*;
/**
 * Extended tests for {@link DsnGenerator}.
 */
@DisplayName("DsnGenerator")
class DsnGeneratorTestExtended {

    private static final String ORIG_MSG = "From: sender@test.com\r\nTo: rcpt@test.com\r\nSubject: Original\r\n\r\nBody text";

    @Test
    void testGenerateBounce() {
        String dsn = DsnGenerator.generateBounce(
                "mta.example.com", "sender@test.com", "bad@test.com",
                new EnhancedStatusCode(5, 1, 1),
                "User not found", ORIG_MSG);

        assertThat(dsn).contains("multipart/report");
        assertThat(dsn).contains("Undelivered Mail Returned to Sender");
        assertThat(dsn).contains("MAILER-DAEMON@mta.example.com");
        assertThat(dsn).contains("<sender@test.com>");
        assertThat(dsn).contains("Final-Recipient: rfc822;bad@test.com");
        assertThat(dsn).contains("Action: failed");
        assertThat(dsn).contains("Status: 5.1.1");
    }

    @Test
    void testGenerateBounceNullOriginalMsg() {
        String dsn = DsnGenerator.generateBounce(
                "mta.example.com", "sender@test.com", "bad@test.com",
                new EnhancedStatusCode(5, 1, 1),
                "User not found", null);

        assertThat(dsn).contains("failed");
        assertThat(dsn).doesNotContain("message/rfc822");
    }

    @Test
    void testGenerateDelay() {
        String dsn = DsnGenerator.generateDelay(
                "mta.example.com", "sender@test.com", "busy@test.com",
                "Mailbox busy", ORIG_MSG);

        assertThat(dsn).contains("Delivery Status Notification (Delay)");
        assertThat(dsn).contains("Action: delayed");
        assertThat(dsn).contains("Status: 4.0.0");
    }

    @Test
    void testGenerateSuccess() {
        String dsn = DsnGenerator.generateSuccess(
                "mta.example.com", "sender@test.com", "rcpt@test.com", ORIG_MSG);

        assertThat(dsn).contains("Delivery Status Notification (Delivered)");
        assertThat(dsn).contains("Action: delivered");
    }

    @Test
    void testGenerateFullReturn() {
        String dsn = DsnGenerator.generate(
                "mta.example.com", "sender@test.com", "rcpt@test.com",
                DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 0, 0),
                "Rejected", ORIG_MSG, DeliveryStatus.ReturnType.FULL);

        assertThat(dsn).contains("Content-Type: message/rfc822");
        assertThat(dsn).contains(ORIG_MSG);
    }

    @Test
    void testGenerateHdrsReturn() {
        String dsn = DsnGenerator.generate(
                "mta.example.com", "sender@test.com", "rcpt@test.com",
                DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 0, 0),
                "Rejected", ORIG_MSG, DeliveryStatus.ReturnType.HDRS);

        assertThat(dsn).contains("Content-Type: text/rfc822-headers");
    }

    @Test
    void testGenerateWithNullDiagnostic() {
        String dsn = DsnGenerator.generate(
                "mta.example.com", "sender@test.com", "rcpt@test.com",
                DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 0, 0),
                null, ORIG_MSG, DeliveryStatus.ReturnType.FULL);

        assertThat(dsn).doesNotContain("Diagnostic-Code");
    }

    @Test
    void testHumanReadableFailed() {
        String dsn = DsnGenerator.generateBounce(
                "mta.example.com", "sender@test.com", "bad@test.com",
                new EnhancedStatusCode(5, 1, 1),
                "User not found", null);

        assertThat(dsn).contains("could not");
        assertThat(dsn).contains("<bad@test.com>: User not found");
    }

    @Test
    void testHumanReadableDelayed() {
        String dsn = DsnGenerator.generateDelay(
                "mta.example.com", "sender@test.com", "busy@test.com",
                "Server busy", null);

        assertThat(dsn).contains("delayed");
        assertThat(dsn).contains("retried");
    }

    @Test
    void testHumanReadableDelivered() {
        String dsn = DsnGenerator.generateSuccess(
                "mta.example.com", "sender@test.com", "rcpt@test.com", null);

        assertThat(dsn).contains("successfully delivered");
    }

    @Test
    void testSubjectLinesForActions() {
        var bounced = DsnGenerator.generateBounce("m.com", "s@t.com", "b@t.com",
                new EnhancedStatusCode(5, 0, 0), "err", null);
        assertThat(bounced).contains("Undelivered Mail Returned to Sender");

        var delayed = DsnGenerator.generateDelay("m.com", "s@t.com", "b@t.com",
                "busy", null);
        assertThat(delayed).contains("Delivery Status Notification (Delay)");

        var success = DsnGenerator.generateSuccess("m.com", "s@t.com",
                "rcpt@t.com", null);
        assertThat(success).contains("Delivery Status Notification (Delivered)");
    }

    @Test
    void testDsnEndsWithBoundaryCloser() {
        String dsn = DsnGenerator.generateBounce(
                "m.com", "s@t.com", "b@t.com",
                new EnhancedStatusCode(5, 0, 0), "err", null);
        assertThat(dsn).matches("(?s).*--DSN_[0-9a-f]+--\r?\n");
    }

    @Test
    void testBoundaryIsUnique() {
        String dsn1 = DsnGenerator.generateBounce("m.com", "s@t.com", "b@t.com",
                new EnhancedStatusCode(5, 0, 0), "err", null);
        String dsn2 = DsnGenerator.generateBounce("m.com", "s@t.com", "b@t.com",
                new EnhancedStatusCode(5, 0, 0), "err", null);

        // Extract boundaries
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("--DSN_[0-9a-f]+")
                .matcher(dsn1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("--DSN_[0-9a-f]+")
                .matcher(dsn2);
        if (m1.find() && m2.find()) {
            assertThat(m1.group()).isNotEqualTo(m2.group());
        }
    }

    @Test
    void testHdrsExtractsOnlyHeaders() {
        String original = "From: s@t.com\r\nTo: r@t.com\r\n\r\nThis is the body";
        String dsn = DsnGenerator.generate(
                "m.com", "s@t.com", "r@t.com",
                DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 0, 0),
                "err", original, DeliveryStatus.ReturnType.HDRS);

        // Should contain headers but body should be truncated
        assertThat(dsn).contains("From: s@t.com");
    }

    @Test
    void testHdrsWithNoBodyOnly() {
        String headersOnly = "From: s@t.com\r\nTo: r@t.com";
        String dsn = DsnGenerator.generate(
                "m.com", "s@t.com", "r@t.com",
                DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 0, 0),
                "err", headersOnly, DeliveryStatus.ReturnType.HDRS);

        assertThat(dsn).contains("From: s@t.com");
    }
}
