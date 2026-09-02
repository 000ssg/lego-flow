package ssg.legoflow.xmpp.client;

import ssg.legoflow.xmpp.auth.SaslMechanism;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link XmppClientConfig}.
 *
 * @since 0.1.0
 */
class XmppClientConfigTest {

    @Test
    void testDefaults() {
        var config = XmppClientConfig.defaults("xmpp.example.com", "example.com");
        assertThat(config.host()).isEqualTo("xmpp.example.com");
        assertThat(config.domain()).isEqualTo("example.com");
        assertThat(config.port()).isEqualTo(5222);
        assertThat(config.enableTls()).isTrue();
        assertThat(config.saslMechanism()).isEqualTo(SaslMechanism.PLAIN);
    }

    @Test
    void testBuilder() {
        var config = XmppClientConfig.builder("host", "domain")
                .port(5223)
                .enableTls(false)
                .saslMechanism(SaslMechanism.SCRAM_SHA_256)
                .connectTimeout(Duration.ofSeconds(10))
                .keepAliveInterval(Duration.ofMinutes(1))
                .build();
        assertThat(config.port()).isEqualTo(5223);
        assertThat(config.enableTls()).isFalse();
        assertThat(config.saslMechanism()).isEqualTo(SaslMechanism.SCRAM_SHA_256);
    }

    @Test
    void testToString() {
        var config = XmppClientConfig.defaults("host", "domain");
        assertThat(config.toString()).contains("host");
        assertThat(config.toString()).contains("domain");
    }

    @Test
    void testNullHostThrows() {
        assertThatThrownBy(() -> XmppClientConfig.defaults(null, "domain"))
                .isInstanceOf(NullPointerException.class);
    }
}
