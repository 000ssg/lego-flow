package ssg.legoflow.email.imap.client;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class ImapClientConfigTest {
    @Test void testDefaults() {
        var config = ImapClientConfig.builder("localhost", 143).build();
        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(143);
        assertThat(config.useTls()).isFalse();
    }

    @Test void testWithCredentials() {
        var config = ImapClientConfig.builder("mail.example.com", 993)
                .credentials("alice", "secret")
                .useTls(true)
                .build();
        assertThat(config.username()).isEqualTo("alice");
        assertThat(config.password()).isEqualTo("secret");
        assertThat(config.useTls()).isTrue();
    }

    @Test void testWithTimeout() {
        var config = ImapClientConfig.builder("host", 143)
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test void testIdleTimeout() {
        var config = ImapClientConfig.builder("host", 143).build();
        assertThat(config.idleTimeout()).isEqualTo(Duration.ofMinutes(25));
    }
}
