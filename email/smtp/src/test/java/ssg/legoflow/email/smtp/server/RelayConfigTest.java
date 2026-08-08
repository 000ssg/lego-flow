package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RelayConfig} and its builder.
 */
class RelayConfigTest {

    @Test
    void testOpenRelayAllowsAll() {
        var config = RelayConfig.openRelay();
        assertThat(config.isOpenRelay()).isTrue();
        assertThat(config.isSenderAllowed("anyone@example.com")).isTrue();
        assertThat(config.isRecipientAllowed("anywhere.org")).isTrue();
    }

    @Test
    void testBuilderOpenRelay() {
        var config = RelayConfig.builder().openRelay(true).build();
        assertThat(config.isOpenRelay()).isTrue();
    }

    @Test
    void testBuilderDefaults() {
        var config = RelayConfig.builder().build();
        assertThat(config.isOpenRelay()).isFalse();
    }

    @Test
    void testAllowSenderList() {
        var config = RelayConfig.builder()
                .allowSender("alice@example.com")
                .allowSender("bob@corp.org")
                .build();
        assertThat(config.isSenderAllowed("alice@example.com")).isTrue();
        assertThat(config.isSenderAllowed("bob@corp.org")).isTrue();
        assertThat(config.isSenderAllowed("eve@evil.com")).isFalse();
    }

    @Test
    void testBlockSenderList() {
        var config = RelayConfig.builder()
                .blockSender("spammer@bad.com")
                .build();
        assertThat(config.isSenderAllowed("good@example.com")).isTrue();
        assertThat(config.isSenderAllowed("spammer@bad.com")).isFalse();
    }

    @Test
    void testAllowDomainForRecipients() {
        var config = RelayConfig.builder()
                .allowDomain("example.com")
                .allowDomain("corp.org")
                .build();
        assertThat(config.isRecipientAllowed("user@example.com")).isTrue();
        assertThat(config.isRecipientAllowed("admin@corp.org")).isTrue();
        assertThat(config.isRecipientAllowed("relay@external.net")).isFalse();
    }

    @Test
    void testRequireAuth() {
        var config = RelayConfig.builder().requireAuth(true).build();
        assertThat(config.requireAuth()).isTrue();
    }

    @Test
    void testMaxMessageSize() {
        var config = RelayConfig.builder().maxMessageSize(1024 * 1024).build();
        assertThat(config.maxMessageSize()).isEqualTo(1024 * 1024);
    }

    @Test
    void testSenderBlockedEvenIfInAllowedList() {
        var config = RelayConfig.builder()
                .allowSender("good@example.com")
                .blockSender("good@example.com")
                .build();
        assertThat(config.isSenderAllowed("good@example.com")).isFalse();
    }

    @Test
    void testBuilderChain() {
        var config = RelayConfig.builder()
                .allowDomain("example.com")
                .allowSender("admin@example.com")
                .requireAuth(true)
                .maxMessageSize(512 * 1024)
                .build();
        assertThat(config.requireAuth()).isTrue();
        assertThat(config.maxMessageSize()).isEqualTo(512 * 1024);
        assertThat(config.isRecipientAllowed("user@example.com")).isTrue();
    }

    @Test
    void testRelayConfigAccessors() {
        var config = RelayConfig.builder()
                .allowDomain("test.com")
                .maxMessageSize(256 * 1024)
                .requireAuth(false)
                .build();
        assertThat(config.isOpenRelay()).isFalse();
        assertThat(config.requireAuth()).isFalse();
        assertThat(config.maxMessageSize()).isEqualTo(256 * 1024);
    }

    @Test
    void testOpenRelayOverrides() {
        var config = RelayConfig.builder()
                .allowDomain("example.com")
                .openRelay(true)
                .build();
        assertThat(config.isOpenRelay()).isTrue();
    }
}
