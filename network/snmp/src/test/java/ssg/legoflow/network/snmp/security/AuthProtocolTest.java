package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link AuthProtocol} enum.
 *
 * @since 0.1.0
 */
class AuthProtocolTest {

    @Test
    void testNone() {
        assertThat(AuthProtocol.NONE.algorithm()).isEqualTo("none");
        assertThat(AuthProtocol.NONE.keyLength()).isZero();
        assertThat(AuthProtocol.NONE.truncatedLength()).isZero();
    }

    @Test
    void testHmacMd596() {
        assertThat(AuthProtocol.HMAC_MD5_96.algorithm()).isEqualTo("HmacMD5");
        assertThat(AuthProtocol.HMAC_MD5_96.keyLength()).isEqualTo(16);
        assertThat(AuthProtocol.HMAC_MD5_96.truncatedLength()).isEqualTo(12);
    }

    @Test
    void testHmacSha96() {
        assertThat(AuthProtocol.HMAC_SHA_96.algorithm()).isEqualTo("HmacSHA1");
        assertThat(AuthProtocol.HMAC_SHA_96.keyLength()).isEqualTo(20);
        assertThat(AuthProtocol.HMAC_SHA_96.truncatedLength()).isEqualTo(12);
    }

    @Test
    void testHashAlgorithmMd5() {
        assertThat(AuthProtocol.HMAC_MD5_96.hashAlgorithm()).isEqualTo("MD5");
    }

    @Test
    void testHashAlgorithmSha() {
        assertThat(AuthProtocol.HMAC_SHA_96.hashAlgorithm()).isEqualTo("SHA-1");
    }

    @Test
    void testHashAlgorithmNoneThrows() {
        assertThatThrownBy(() -> AuthProtocol.NONE.hashAlgorithm())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No hash for NONE auth");
    }
}
