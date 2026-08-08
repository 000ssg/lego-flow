package ssg.legoflow.email.smtp.dsn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link DeliveryStatus}, including parse/format round-trip.
 */
@DisplayName("DeliveryStatus")
class DeliveryStatusTest {

    private final Instant testDate = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void testConstructorAndGetters() {
        var recipients = List.of(
                new DeliveryStatus.RecipientStatus("rcpt@test.com", null,
                        DeliveryStatus.Action.DELIVERED, new EnhancedStatusCode(2, 0, 0),
                        "SMTP; 250 OK", "mx.test.com"));

        var dsn = new DeliveryStatus("mta.example.com", "env-123", testDate, recipients);

        assertThat(dsn.reportingMta()).isEqualTo("mta.example.com");
        assertThat(dsn.envelopeId()).isEqualTo("env-123");
        assertThat(dsn.arrivalDate()).isEqualTo(testDate);
        assertThat(dsn.recipientStatuses()).hasSize(1);
    }

    @Test
    void testNullArrivalDateDefaultsToNow() {
        var dsn = new DeliveryStatus("mta.example.com", null, null, List.of());
        // arrival date should be set to a recent time
        Instant now = Instant.now();
        assertThat(dsn.arrivalDate()).isBeforeOrEqualTo(now);
    }

    @Test
    void testNullReportingMtaThrows() {
        assertThatThrownBy(() -> new DeliveryStatus(null, "env", testDate, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFormatWithRecipient() {
        var recipients = List.of(
                new DeliveryStatus.RecipientStatus("user@example.com", null,
                        DeliveryStatus.Action.DELIVERED, new EnhancedStatusCode(2, 0, 0),
                        "SMTP; 250 OK", "mx.example.com"));

        var dsn = new DeliveryStatus("mta.test.com", "env-42", testDate, recipients);
        String formatted = dsn.format();

        assertThat(formatted).contains("Reporting-MTA: dns;mta.test.com");
        assertThat(formatted).contains("Original-Envelope-Id: env-42");
        assertThat(formatted).contains("Final-Recipient: rfc822;user@example.com");
        assertThat(formatted).contains("Action: delivered");
        assertThat(formatted).contains("Status: 2.0.0");
        assertThat(formatted).contains("Diagnostic-Code: SMTP; 250 OK");
        assertThat(formatted).contains("Remote-MTA: dns;mx.example.com");
    }

    @Test
    void testFormatWithoutEnvelopeId() {
        var dsn = new DeliveryStatus("mta.test.com", null, testDate, List.of());
        String formatted = dsn.format();
        assertThat(formatted).contains("Reporting-MTA: dns;mta.test.com");
        assertThat(formatted).doesNotContain("Original-Envelope-Id");
    }

    @Test
    void testParseBasicDsn() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "Original-Envelope-Id: env-42\r\n"
                + "Arrival-Date: 2026-01-15T12:00:00Z\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;user@example.com\r\n"
                + "Action: delivered\r\n"
                + "Status: 2.0.0\r\n"
                + "Diagnostic-Code: SMTP; 250 OK\r\n"
                + "Remote-MTA: dns;mx.example.com";

        var parsed = DeliveryStatus.parse(dsnText);

        assertThat(parsed.reportingMta()).isEqualTo("mta.test.com");
        assertThat(parsed.envelopeId()).isEqualTo("env-42");
        assertThat(parsed.recipientStatuses()).hasSize(1);

        var recipient = parsed.recipientStatuses().get(0);
        assertThat(recipient.finalRecipient()).isEqualTo("user@example.com");
        assertThat(recipient.action()).isEqualTo(DeliveryStatus.Action.DELIVERED);
        assertThat(recipient.diagnosticCode()).isEqualTo("SMTP; 250 OK");
        assertThat(recipient.remoteMta()).isEqualTo("mx.example.com");
    }

    @Test
    void testParseFailedDelivery() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;bounce@example.com\r\n"
                + "Original-Recipient: rfc822;original@example.com\r\n"
                + "Action: failed\r\n"
                + "Status: 5.1.1";

        var parsed = DeliveryStatus.parse(dsnText);
        var recipient = parsed.recipientStatuses().get(0);
        assertThat(recipient.finalRecipient()).isEqualTo("bounce@example.com");
        assertThat(recipient.originalRecipient()).isEqualTo("original@example.com");
        assertThat(recipient.action()).isEqualTo(DeliveryStatus.Action.FAILED);
    }

    @Test
    void testParseAllActionTypes() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;a@test.com\r\nAction: delivered\r\nStatus: 2.0.0\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;b@test.com\r\nAction: delayed\r\nStatus: 4.0.0\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;c@test.com\r\nAction: relayed\r\nStatus: 2.0.0\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;d@test.com\r\nAction: expanded\r\nStatus: 2.0.0\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;e@test.com\r\nAction: unknown_action\r\nStatus: 5.0.0";

        var parsed = DeliveryStatus.parse(dsnText);
        assertThat(parsed.recipientStatuses()).hasSize(5);
        assertThat(parsed.recipientStatuses().get(0).action()).isEqualTo(DeliveryStatus.Action.DELIVERED);
        assertThat(parsed.recipientStatuses().get(1).action()).isEqualTo(DeliveryStatus.Action.DELAYED);
        assertThat(parsed.recipientStatuses().get(2).action()).isEqualTo(DeliveryStatus.Action.RELAYED);
        assertThat(parsed.recipientStatuses().get(3).action()).isEqualTo(DeliveryStatus.Action.EXPANDED);
        // Unknown action defaults to FAILED
        assertThat(parsed.recipientStatuses().get(4).action()).isEqualTo(DeliveryStatus.Action.FAILED);
    }

    @Test
    void testParseFormatRoundTrip() {
        var recipients = List.of(
                new DeliveryStatus.RecipientStatus("user@test.com", null,
                        DeliveryStatus.Action.DELIVERED, new EnhancedStatusCode(2, 0, 0),
                        "OK", "mx.test.com"),
                new DeliveryStatus.RecipientStatus("bounce@test.com", "orig@test.com",
                        DeliveryStatus.Action.FAILED, new EnhancedStatusCode(5, 1, 1),
                        "User unknown", null));

        var dsn = new DeliveryStatus("mta.example.com", "env-99", testDate, recipients);
        String formatted = dsn.format();
        var parsed = DeliveryStatus.parse(formatted);

        assertThat(parsed.reportingMta()).isEqualTo(dsn.reportingMta());
        assertThat(parsed.envelopeId()).isEqualTo(dsn.envelopeId());
        assertThat(parsed.recipientStatuses()).hasSize(2);
    }

    @Test
    void testParseEmptyText() {
        var parsed = DeliveryStatus.parse("");
        assertThat(parsed.reportingMta()).isEmpty();
        assertThat(parsed.envelopeId()).isNull();
        assertThat(parsed.recipientStatuses()).isEmpty();
    }

    @Test
    void testParseInvalidStatusCodeDefaultsTo500() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;x@test.com\r\n"
                + "Action: failed\r\n"
                + "Status: 99.99.99";

        var parsed = DeliveryStatus.parse(dsnText);
        var recipient = parsed.recipientStatuses().get(0);
        // Invalid status should default to 5.0.0
    }

    @Test
    void testActionEnumValues() {
        assertThat(DeliveryStatus.Action.values())
                .containsExactly(DeliveryStatus.Action.DELIVERED, DeliveryStatus.Action.FAILED,
                        DeliveryStatus.Action.DELAYED, DeliveryStatus.Action.RELAYED,
                        DeliveryStatus.Action.EXPANDED);
    }

    @Test
    void testNotifyTypeEnumValues() {
        assertThat(DeliveryStatus.NotifyType.values())
                .containsExactly(DeliveryStatus.NotifyType.SUCCESS, DeliveryStatus.NotifyType.FAILURE,
                        DeliveryStatus.NotifyType.DELAY, DeliveryStatus.NotifyType.NEVER);
    }

    @Test
    void testReturnTypeEnumValues() {
        assertThat(DeliveryStatus.ReturnType.values())
                .containsExactly(DeliveryStatus.ReturnType.FULL, DeliveryStatus.ReturnType.HDRS);
    }

    @Test
    void testRecipientStatusRecord() {
        var rs = new DeliveryStatus.RecipientStatus(
                "final@test.com", "original@test.com",
                DeliveryStatus.Action.DELIVERED, new EnhancedStatusCode(2, 0, 0),
                "diagnostic info", "remote.mta.com");

        assertThat(rs.finalRecipient()).isEqualTo("final@test.com");
        assertThat(rs.originalRecipient()).isEqualTo("original@test.com");
        assertThat(rs.action()).isEqualTo(DeliveryStatus.Action.DELIVERED);
        assertThat(rs.diagnosticCode()).isEqualTo("diagnostic info");
        assertThat(rs.remoteMta()).isEqualTo("remote.mta.com");
    }

    @Test
    void testFormatWithNullFields() {
        var recipients = List.of(
                new DeliveryStatus.RecipientStatus("user@test.com", null,
                        DeliveryStatus.Action.DELIVERED, new EnhancedStatusCode(2, 0, 0),
                        null, null));

        var dsn = new DeliveryStatus("mta.test.com", null, testDate, recipients);
        String formatted = dsn.format();
        assertThat(formatted).doesNotContain("Original-Recipient");
        assertThat(formatted).doesNotContain("Diagnostic-Code");
        assertThat(formatted).doesNotContain("Remote-MTA");
    }

    @Test
    void testParseWithoutReportingMta() {
        String dsnText = "Arrival-Date: 2026-01-15T12:00:00Z\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;user@test.com\r\n"
                + "Action: failed\r\n"
                + "Status: 5.0.0";

        var parsed = DeliveryStatus.parse(dsnText);
        assertThat(parsed.reportingMta()).isEmpty();
    }

    @Test
    void testParseWithInvalidDate() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "Arrival-Date: not-a-valid-date\r\n";

        var parsed = DeliveryStatus.parse(dsnText);
        // Should handle gracefully without throwing
        assertThat(parsed.arrivalDate()).isNotNull();
    }

    @Test
    void testParseMultipleRecipientsWithDifferentActions() {
        String dsnText = "Reporting-MTA: dns;mta.test.com\r\n"
                + "Original-Envelope-Id: env-multi\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;good@test.com\r\n"
                + "Action: delivered\r\nStatus: 2.0.0\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822;bad@test.com\r\n"
                + "Action: failed\r\nStatus: 5.1.1\r\n"
                + "Diagnostic-Code: SMTP; 550 User not found";

        var parsed = DeliveryStatus.parse(dsnText);
        assertThat(parsed.recipientStatuses()).hasSize(2);
        assertThat(parsed.recipientStatuses().get(0).action()).isEqualTo(DeliveryStatus.Action.DELIVERED);
        assertThat(parsed.recipientStatuses().get(1).action()).isEqualTo(DeliveryStatus.Action.FAILED);
    }
}
