package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link PrivProtocol} enum.
 *
 * @since 0.1.0
 */
class PrivProtocolTest {

    @Test
    void testNone() {
        assertThat(PrivProtocol.NONE.algorithm()).isEqualTo("none");
        assertThat(PrivProtocol.NONE.keyLength()).isZero();
        assertThat(PrivProtocol.NONE.ivLength()).isZero();
    }

    @Test
    void testDesCbc() {
        assertThat(PrivProtocol.DES_CBC.algorithm()).isEqualTo("DES/CBC/NoPadding");
        assertThat(PrivProtocol.DES_CBC.keyLength()).isEqualTo(16);
        assertThat(PrivProtocol.DES_CBC.ivLength()).isEqualTo(8);
    }

    @Test
    void testAes128Cfb() {
        assertThat(PrivProtocol.AES_128_CFB.algorithm()).isEqualTo("AES/CFB/NoPadding");
        assertThat(PrivProtocol.AES_128_CFB.keyLength()).isEqualTo(16);
        assertThat(PrivProtocol.AES_128_CFB.ivLength()).isEqualTo(16);
    }
}
