package ssg.legoflow.ssh.client;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SshClientConfigTest {

    @Test
    void testDefaultConfig() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.authTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.strictHostKeyChecking()).isTrue();
    }

    @Test
    void testDefaultKexAlgorithms() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.preferredKexAlgorithms()).contains("curve25519-sha256");
    }

    @Test
    void testDefaultHostKeyAlgorithms() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.preferredHostKeyAlgorithms()).contains("ssh-ed25519");
    }

    @Test
    void testDefaultCiphers() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.preferredCiphers())
                .contains("chacha20-poly1305@openssh.com", "aes256-ctr");
    }

    @Test
    void testDefaultMacs() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.preferredMacs()).contains("hmac-sha2-256");
    }

    @Test
    void testDefaultCompressions() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThat(config.preferredCompressions()).contains("none");
    }

    @Test
    void testCustomKexAlgorithms() {
        SshClientConfig config = SshClientConfig.builder()
                .preferredKexAlgorithms(List.of("ecdh-sha2-nistp256"))
                .build();
        assertThat(config.preferredKexAlgorithms()).containsExactly("ecdh-sha2-nistp256");
    }

    @Test
    void testCustomCiphers() {
        SshClientConfig config = SshClientConfig.builder()
                .preferredCiphers(List.of("aes128-ctr"))
                .build();
        assertThat(config.preferredCiphers()).containsExactly("aes128-ctr");
    }

    @Test
    void testCustomTimeout() {
        SshClientConfig config = SshClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .authTimeout(Duration.ofSeconds(5))
                .build();
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.authTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void testStrictHostKeyCheckingDisabled() {
        SshClientConfig config = SshClientConfig.builder()
                .strictHostKeyChecking(false)
                .build();
        assertThat(config.strictHostKeyChecking()).isFalse();
    }

    @Test
    void testBuilderChaining() {
        SshClientConfig config = SshClientConfig.builder()
                .preferredKexAlgorithms(List.of("curve25519-sha256"))
                .preferredHostKeyAlgorithms(List.of("ssh-ed25519"))
                .preferredCiphers(List.of("aes256-ctr"))
                .preferredMacs(List.of("hmac-sha2-256"))
                .preferredCompressions(List.of("none"))
                .connectTimeout(Duration.ofSeconds(15))
                .authTimeout(Duration.ofSeconds(20))
                .strictHostKeyChecking(false)
                .build();
        assertThat(config.preferredKexAlgorithms()).hasSize(1);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void testNullConnectTimeoutThrows() {
        assertThatThrownBy(() -> SshClientConfig.builder().connectTimeout(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullAuthTimeoutThrows() {
        assertThatThrownBy(() -> SshClientConfig.builder().authTimeout(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testImmutableLists() {
        SshClientConfig config = SshClientConfig.defaults();
        assertThatThrownBy(() -> config.preferredKexAlgorithms().add("test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
