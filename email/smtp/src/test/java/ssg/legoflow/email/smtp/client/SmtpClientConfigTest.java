package ssg.legoflow.email.smtp.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Unit tests for {@link SmtpClientConfig} and its builder.
 */
@DisplayName("SmtpClientConfig")
class SmtpClientConfigTest {

    @Test
    void testBuilderBasic() {
        var config = SmtpClientConfig.builder("smtp.example.com", 587).build();
        assertThat(config.host()).isEqualTo("smtp.example.com");
        assertThat(config.port()).isEqualTo(587);
        assertThat(config.tlsMode()).isEqualTo(SmtpClientConfig.TlsMode.NONE);
        assertThat(config.localHostname()).isEqualTo("localhost");
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.pipelining()).isTrue();
        assertThat(config.sslContext()).isNull();
        assertThat(config.username()).isNull();
        assertThat(config.password()).isNull();
        assertThat(config.authMechanism()).isNull();
    }

    @Test
    void testBuilderWithAllOptions() throws Exception {
        SSLContext sslCtx = SSLContext.getInstance("TLSv1.3");
        sslCtx.init(null, null, null);

        var config = SmtpClientConfig.builder("smtp.secure.com", 465)
                .tlsMode(SmtpClientConfig.TlsMode.IMPLICIT)
                .sslContext(sslCtx)
                .auth("user@example.com", "secret123")
                .authMechanism("PLAIN")
                .localHostname("myhost.example.com")
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(120))
                .pipelining(false)
                .build();

        assertThat(config.host()).isEqualTo("smtp.secure.com");
        assertThat(config.port()).isEqualTo(465);
        assertThat(config.tlsMode()).isEqualTo(SmtpClientConfig.TlsMode.IMPLICIT);
        assertThat(config.sslContext()).isSameAs(sslCtx);
        assertThat(config.username()).isEqualTo("user@example.com");
        assertThat(config.password()).isEqualTo("secret123");
        assertThat(config.authMechanism()).isEqualTo("PLAIN");
        assertThat(config.localHostname()).isEqualTo("myhost.example.com");
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(config.pipelining()).isFalse();
        assertThat(config.hasAuth()).isTrue();
    }

    @Test
    void testHasAuthWithoutCredentials() {
        var config = SmtpClientConfig.builder("smtp.example.com", 25).build();
        assertThat(config.hasAuth()).isFalse();
    }

    @Test
    void testHasAuthWithPartialCredentials() {
        var config = SmtpClientConfig.builder("smtp.example.com", 25)
                .auth("user@example.com", null)
                .build();
        // hasAuth requires both username and password non-null
        assertThat(config.hasAuth()).isFalse();

        var config2 = SmtpClientConfig.builder("smtp.example.com", 25)
                .auth(null, "secret")
                .build();
        assertThat(config2.hasAuth()).isFalse();
    }

    @Test
    void testTlsModeValues() {
        assertThat(SmtpClientConfig.TlsMode.values()).containsExactly(
                SmtpClientConfig.TlsMode.NONE,
                SmtpClientConfig.TlsMode.STARTTLS,
                SmtpClientConfig.TlsMode.IMPLICIT
        );
    }

    @Test
    void testBuilderRequiresNonNullHost() {
        assertThatThrownBy(() -> SmtpClientConfig.builder(null, 25))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderStartTlsMode() {
        var config = SmtpClientConfig.builder("smtp.example.com", 587)
                .tlsMode(SmtpClientConfig.TlsMode.STARTTLS)
                .build();
        assertThat(config.tlsMode()).isEqualTo(SmtpClientConfig.TlsMode.STARTTLS);
    }

    @Test
    void testMultipleBuildsFromSameBuilder() {
        var builder = SmtpClientConfig.builder("smtp.example.com", 25);
        var config1 = builder.auth("user1", "pass1").build();
        var config2 = builder.auth("user2", "pass2").build();

        // Builder state is mutated, so second build should have second values
        assertThat(config2.username()).isEqualTo("user2");
    }
}
