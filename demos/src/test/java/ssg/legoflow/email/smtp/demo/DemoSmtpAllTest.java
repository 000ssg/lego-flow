package ssg.legoflow.email.smtp.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive SMTP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code SmtpServer}. To test against
 * an external Postfix, Gmail SMTP, Microsoft Exchange, or Amazon SES,
 * set {@code DemoSmtpAll.USE_EXTERNAL = true} and configure host/port
 * before running.</p>
 */
class DemoSmtpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSmtpAll.runAll();

        assertThat(results.basicSend())
                .as("Basic single-message send succeeds")
                .isTrue();

        assertThat(results.multiRecipient())
                .as("Multi-recipient send delivers to all recipients")
                .isEqualTo(4);

        assertThat(results.mimeMessage())
                .as("MIME-formatted message accepted")
                .isTrue();

        assertThat(results.authentication())
                .as("SASL PLAIN authentication with relay restrictions")
                .isTrue();

        assertThat(results.sessionManagement())
                .as("RSET/NOOP and multiple transactions on one connection")
                .isTrue();

        assertThat(results.extensionCount())
                .as("At least one ESMTP extension negotiated")
                .isGreaterThanOrEqualTo(1);
    }
}
