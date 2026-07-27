package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class X11ForwardingConfigTest {

    @Test
    void testGenerateCreatesValidConfig() {
        X11ForwardingConfig config = X11ForwardingConfig.generate();
        assertThat(config).isNotNull();
        assertThat(config.singleConnection()).isFalse();
        assertThat(config.screenNumber()).isEqualTo(0);
    }

    @Test
    void testGenerateUsesMitMagicCookie1() {
        X11ForwardingConfig config = X11ForwardingConfig.generate();
        assertThat(config.authProtocol()).isEqualTo("MIT-MAGIC-COOKIE-1");
    }

    @Test
    void testGenerateCookieIs16Bytes() {
        X11ForwardingConfig config = X11ForwardingConfig.generate();
        assertThat(config.authCookie()).hasSize(16);
    }

    @Test
    void testGenerateCookieIsRandom() {
        X11ForwardingConfig config1 = X11ForwardingConfig.generate();
        X11ForwardingConfig config2 = X11ForwardingConfig.generate();
        assertThat(config1.authCookie()).isNotEqualTo(config2.authCookie());
    }

    @Test
    void testGenerateWithParameters() {
        X11ForwardingConfig config = X11ForwardingConfig.generate(true, 2);
        assertThat(config.singleConnection()).isTrue();
        assertThat(config.screenNumber()).isEqualTo(2);
        assertThat(config.authProtocol()).isEqualTo("MIT-MAGIC-COOKIE-1");
        assertThat(config.authCookie()).hasSize(16);
    }

    @Test
    void testCustomConfig() {
        byte[] cookie = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        X11ForwardingConfig config = new X11ForwardingConfig(false, "MIT-MAGIC-COOKIE-1", cookie, 0);
        assertThat(config.authCookie()).isEqualTo(cookie);
    }

    @Test
    void testCookieDefensiveCopy() {
        byte[] cookie = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        X11ForwardingConfig config = new X11ForwardingConfig(false, "MIT-MAGIC-COOKIE-1", cookie, 0);
        cookie[0] = 99; // mutate original
        assertThat(config.authCookie()[0]).isNotEqualTo((byte) 99);
    }

    @Test
    void testNegativeScreenNumberThrows() {
        assertThatThrownBy(() ->
                new X11ForwardingConfig(false, "MIT-MAGIC-COOKIE-1", new byte[16], -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullAuthProtocolThrows() {
        assertThatThrownBy(() ->
                new X11ForwardingConfig(false, null, new byte[16], 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMitMagicCookie1Constant() {
        assertThat(X11ForwardingConfig.MIT_MAGIC_COOKIE_1).isEqualTo("MIT-MAGIC-COOKIE-1");
    }

    @Test
    void testDefaultCookieLengthConstant() {
        assertThat(X11ForwardingConfig.DEFAULT_COOKIE_LENGTH).isEqualTo(16);
    }
}
