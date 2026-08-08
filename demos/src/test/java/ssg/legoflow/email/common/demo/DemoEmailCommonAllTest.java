package ssg.legoflow.email.common.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive email/common MIME demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoEmailCommonAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoEmailCommonAll.runAll();

        assertThat(results.mimeParsing())
                .as("MIME parsing produces valid message")
                .isTrue();

        assertThat(results.mimeWriting())
                .as("MIME writing produces valid bytes")
                .isTrue();

        assertThat(results.roundTrip())
                .as("Parse-write-parse round-trip preserves data")
                .isTrue();

        assertThat(results.messageBuilder())
                .as("Message builder produces valid message")
                .isTrue();

        assertThat(results.multipartMessage())
                .as("Multipart message construction and parsing")
                .isTrue();

        assertThat(results.contentEncodings())
                .as("Base64 and Quoted-Printable round-trip")
                .isTrue();

        assertThat(results.encodedWords())
                .as("RFC 2047 encoded word round-trip")
                .isTrue();

        assertThat(results.emailAddresses())
                .as("Email address parsing")
                .isTrue();

        assertThat(results.contentTypeParsing())
                .as("Content type parsing")
                .isTrue();
    }
}
