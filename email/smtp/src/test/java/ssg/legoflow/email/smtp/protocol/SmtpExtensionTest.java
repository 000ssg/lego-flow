package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SmtpExtension}.
 */
class SmtpExtensionTest {

    @ParameterizedTest
    @EnumSource(SmtpExtension.class)
    void testFromKeywordRoundTrip(SmtpExtension ext) {
        assertThat(SmtpExtension.fromKeyword(ext.keyword())).contains(ext);
    }

    @Test
    void testFromKeywordCaseInsensitive() {
        assertThat(SmtpExtension.fromKeyword("size")).contains(SmtpExtension.SIZE);
        assertThat(SmtpExtension.fromKeyword("Size")).contains(SmtpExtension.SIZE);
        assertThat(SmtpExtension.fromKeyword("SIZE")).contains(SmtpExtension.SIZE);
    }

    @Test
    void testFromKeywordUnknown() {
        assertThat(SmtpExtension.fromKeyword("UNKNOWN")).isEmpty();
    }

    @Test
    void testFromKeywordNull() {
        assertThat(SmtpExtension.fromKeyword(null)).isEmpty();
    }

    @Test
    void testFromKeywordBlank() {
        assertThat(SmtpExtension.fromKeyword("")).isEmpty();
        assertThat(SmtpExtension.fromKeyword("   ")).isEmpty();
    }

    @Test
    void testParseEhloBasic() {
        var lines = List.of(
                "mail.example.com Hello client",
                "SIZE 10485760",
                "8BITMIME",
                "STARTTLS",
                "PIPELINING",
                "ENHANCEDSTATUSCODES"
        );
        var extensions = SmtpExtension.parseEhlo(lines);
        assertThat(extensions).containsKey(SmtpExtension.SIZE);
        assertThat(extensions).containsKey(SmtpExtension.EIGHT_BIT_MIME);
        assertThat(extensions).containsKey(SmtpExtension.STARTTLS);
        assertThat(extensions).containsKey(SmtpExtension.PIPELINING);
        assertThat(extensions).containsKey(SmtpExtension.ENHANCED_STATUS_CODES);
    }

    @Test
    void testParseEhloWithAuth() {
        var lines = List.of(
                "mail.example.com",
                "AUTH PLAIN LOGIN CRAM-MD5"
        );
        var extensions = SmtpExtension.parseEhlo(lines);
        assertThat(extensions).containsKey(SmtpExtension.AUTH);
        assertThat(extensions.get(SmtpExtension.AUTH)).isEqualTo("PLAIN LOGIN CRAM-MD5");
    }

    @Test
    void testParseEhloSizeParam() {
        var lines = List.of("host", "SIZE 52428800");
        var extensions = SmtpExtension.parseEhlo(lines);
        assertThat(extensions.get(SmtpExtension.SIZE)).isEqualTo("52428800");
    }

    @Test
    void testParseEhloSkipsFirstLine() {
        var lines = List.of("mail.example.com Hello");
        var extensions = SmtpExtension.parseEhlo(lines);
        assertThat(extensions).isEmpty();
    }

    @Test
    void testParseEhloIgnoresUnknownExtensions() {
        var lines = List.of("host", "CUSTOM_EXT param", "SIZE 1024");
        var extensions = SmtpExtension.parseEhlo(lines);
        assertThat(extensions).hasSize(1);
        assertThat(extensions).containsKey(SmtpExtension.SIZE);
    }

    @Test
    void testParseAuthMechanisms() {
        var mechanisms = SmtpExtension.parseAuthMechanisms("PLAIN LOGIN CRAM-MD5 XOAUTH2");
        assertThat(mechanisms).containsExactly("PLAIN", "LOGIN", "CRAM-MD5", "XOAUTH2");
    }

    @Test
    void testParseAuthMechanismsEmpty() {
        assertThat(SmtpExtension.parseAuthMechanisms("")).isEmpty();
        assertThat(SmtpExtension.parseAuthMechanisms(null)).isEmpty();
        assertThat(SmtpExtension.parseAuthMechanisms("  ")).isEmpty();
    }

    @Test
    void testParseSizeLimit() {
        assertThat(SmtpExtension.parseSizeLimit("10485760")).isEqualTo(10485760L);
        assertThat(SmtpExtension.parseSizeLimit("0")).isEqualTo(0L);
    }

    @Test
    void testParseSizeLimitEmpty() {
        assertThat(SmtpExtension.parseSizeLimit("")).isEqualTo(0L);
        assertThat(SmtpExtension.parseSizeLimit(null)).isEqualTo(0L);
    }

    @Test
    void testParseSizeLimitInvalid() {
        assertThat(SmtpExtension.parseSizeLimit("abc")).isEqualTo(0L);
    }

    @Test
    void testKeywords() {
        assertThat(SmtpExtension.SIZE.keyword()).isEqualTo("SIZE");
        assertThat(SmtpExtension.EIGHT_BIT_MIME.keyword()).isEqualTo("8BITMIME");
        assertThat(SmtpExtension.STARTTLS.keyword()).isEqualTo("STARTTLS");
        assertThat(SmtpExtension.AUTH.keyword()).isEqualTo("AUTH");
        assertThat(SmtpExtension.PIPELINING.keyword()).isEqualTo("PIPELINING");
        assertThat(SmtpExtension.CHUNKING.keyword()).isEqualTo("CHUNKING");
        assertThat(SmtpExtension.DSN.keyword()).isEqualTo("DSN");
        assertThat(SmtpExtension.ENHANCED_STATUS_CODES.keyword()).isEqualTo("ENHANCEDSTATUSCODES");
        assertThat(SmtpExtension.SMTPUTF8.keyword()).isEqualTo("SMTPUTF8");
    }

    @Test
    void testAllExtensionsCount() {
        assertThat(SmtpExtension.values()).hasSize(9);
    }
}
