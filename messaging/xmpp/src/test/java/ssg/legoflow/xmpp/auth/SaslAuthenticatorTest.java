package ssg.legoflow.xmpp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SaslAuthenticator}.
 *
 * @since 0.1.0
 */
class SaslAuthenticatorTest {

    private SaslAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new SaslAuthenticator();
    }

    @Test
    void testPlainAuthentication() {
        boolean result = authenticator.authenticate(SaslMechanism.PLAIN, "user", "pass").join();
        assertThat(result).isTrue();
        assertThat(authenticator.isAuthenticated()).isTrue();
        assertThat(authenticator.getUsername()).isEqualTo("user");
    }

    @Test
    void testGeneratePlainAuth() {
        String encoded = authenticator.generatePlainAuth("user", "pass");
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String plain = new String(decoded);
        assertThat(plain).isEqualTo("\0user\0pass");
    }

    @Test
    void testScramAuthentication() {
        boolean result = authenticator.authenticate(SaslMechanism.SCRAM_SHA_1, "user", "pass").join();
        assertThat(result).isTrue();
        assertThat(authenticator.getCurrentMechanism()).isEqualTo(SaslMechanism.SCRAM_SHA_1);
    }

    @Test
    void testProcessChallenge() {
        authenticator.authenticate(SaslMechanism.SCRAM_SHA_1, "user", "pass").join();
        String response = authenticator.processChallenge("c2VydmVyLWNoYWxsZW5nZQ==");
        assertThat(response).isNotEmpty();
    }

    @Test
    void testGenerateAuthXml() {
        String xml = authenticator.generateAuthXml(SaslMechanism.PLAIN, "user", "pass");
        assertThat(xml).contains("mechanism='PLAIN'");
        assertThat(xml).contains("xmlns='urn:ietf:params:xml:ns:xmpp-sasl'");
    }

    @Test
    void testAnonymousAuthentication() {
        boolean result = authenticator.authenticate(SaslMechanism.ANONYMOUS, "ignored", "ignored").join();
        assertThat(result).isTrue();
        assertThat(authenticator.getUsername()).isEqualTo("anonymous");
    }
}
