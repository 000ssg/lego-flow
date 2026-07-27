package ssg.legoflow.ssh.server;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SshServerConfigTest {

    @Test
    void testDefaultPort() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.port()).isEqualTo(22);
    }

    @Test
    void testDefaultBindAddress() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.bindAddress()).isEqualTo("0.0.0.0");
    }

    @Test
    void testDefaultAuthTimeout() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.authTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void testDefaultMaxAuthAttempts() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.maxAuthAttempts()).isEqualTo(6);
    }

    @Test
    void testDefaultMaxConcurrentConnections() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.maxConcurrentConnections()).isEqualTo(100);
    }

    @Test
    void testDefaultCiphers() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.preferredCiphers()).contains("aes256-ctr", "aes128-ctr");
    }

    @Test
    void testDefaultMacs() {
        SshServerConfig config = SshServerConfig.defaults();
        assertThat(config.preferredMacs()).contains("hmac-sha2-256", "hmac-sha2-512");
    }

    @Test
    void testCustomPort() {
        SshServerConfig config = SshServerConfig.builder().port(2222).build();
        assertThat(config.port()).isEqualTo(2222);
    }

    @Test
    void testCustomBindAddress() {
        SshServerConfig config = SshServerConfig.builder().bindAddress("127.0.0.1").build();
        assertThat(config.bindAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void testCustomMaxAuthAttempts() {
        SshServerConfig config = SshServerConfig.builder().maxAuthAttempts(3).build();
        assertThat(config.maxAuthAttempts()).isEqualTo(3);
    }

    @Test
    void testCustomMaxConcurrentConnections() {
        SshServerConfig config = SshServerConfig.builder().maxConcurrentConnections(50).build();
        assertThat(config.maxConcurrentConnections()).isEqualTo(50);
    }

    @Test
    void testBuilderChaining() {
        SshServerConfig config = SshServerConfig.builder()
                .port(2222)
                .bindAddress("127.0.0.1")
                .authTimeout(Duration.ofSeconds(60))
                .maxAuthAttempts(3)
                .maxConcurrentConnections(50)
                .preferredCiphers(List.of("aes256-ctr"))
                .preferredMacs(List.of("hmac-sha2-512"))
                .build();
        assertThat(config.port()).isEqualTo(2222);
        assertThat(config.bindAddress()).isEqualTo("127.0.0.1");
        assertThat(config.authTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
}
