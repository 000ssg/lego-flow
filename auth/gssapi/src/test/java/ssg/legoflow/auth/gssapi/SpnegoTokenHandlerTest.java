package ssg.legoflow.auth.gssapi;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SpnegoTokenHandler}: NegTokenInit/NegTokenResp creation,
 * SPNEGO token detection, Base64 encoding/decoding, and token extraction.
 */
class SpnegoTokenHandlerTest {

    @Test
    void testCreateNegTokenInitNotNull() {
        byte[] result = SpnegoTokenHandler.createNegTokenInit(new byte[]{1, 2, 3});
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void testCreateNegTokenInitStartsWithApplicationTag() {
        byte[] result = SpnegoTokenHandler.createNegTokenInit(new byte[]{1, 2, 3});
        // APPLICATION [0] = 0x60
        assertThat(result[0] & 0xff).isEqualTo(0x60);
    }

    @Test
    void testCreateNegTokenInitContainsSpnegoOid() {
        byte[] result = SpnegoTokenHandler.createNegTokenInit(new byte[]{0x01});
        // SPNEGO OID bytes: 06 06 2b 06 01 05 05 02
        assertThat(containsBytes(result, new byte[]{0x06, 0x06, 0x2b, 0x06, 0x01, 0x05, 0x05, 0x02}))
                .isTrue();
    }

    @Test
    void testCreateNegTokenInitContainsKerberosOid() {
        byte[] result = SpnegoTokenHandler.createNegTokenInit(new byte[]{0x01});
        // Kerberos OID: 06 09 2a 86 48 86 f7 12 01 02 02
        assertThat(containsBytes(result, new byte[]{
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x12, 0x01, 0x02, 0x02
        })).isTrue();
    }

    @Test
    void testCreateNegTokenInitNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.createNegTokenInit(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateNegTokenRespNotNull() {
        byte[] result = SpnegoTokenHandler.createNegTokenResp(new byte[]{1, 2}, true);
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void testCreateNegTokenRespStartsWithContextTag() {
        byte[] result = SpnegoTokenHandler.createNegTokenResp(new byte[]{1}, true);
        // NegTokenResp [1] = 0xa1
        assertThat(result[0] & 0xff).isEqualTo(0xa1);
    }

    @Test
    void testCreateNegTokenRespCompleteContainsAcceptCompleted() {
        byte[] result = SpnegoTokenHandler.createNegTokenResp(new byte[]{1}, true);
        // ENUMERATED 0 (accept-completed)
        assertThat(containsBytes(result, new byte[]{0x0a, 0x01, 0x00})).isTrue();
    }

    @Test
    void testCreateNegTokenRespIncompleteContainsAcceptIncomplete() {
        byte[] result = SpnegoTokenHandler.createNegTokenResp(new byte[]{1}, false);
        // ENUMERATED 1 (accept-incomplete)
        assertThat(containsBytes(result, new byte[]{0x0a, 0x01, 0x01})).isTrue();
    }

    @Test
    void testCreateNegTokenRespNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.createNegTokenResp(null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIsSpnegoTokenWithNegTokenInit() {
        byte[] token = SpnegoTokenHandler.createNegTokenInit(new byte[]{1, 2, 3});
        assertThat(SpnegoTokenHandler.isSpnegoToken(token)).isTrue();
    }

    @Test
    void testIsSpnegoTokenWithNegTokenResp() {
        byte[] token = SpnegoTokenHandler.createNegTokenResp(new byte[]{1}, true);
        assertThat(SpnegoTokenHandler.isSpnegoToken(token)).isTrue();
    }

    @Test
    void testIsSpnegoTokenWithNull() {
        assertThat(SpnegoTokenHandler.isSpnegoToken(null)).isFalse();
    }

    @Test
    void testIsSpnegoTokenWithEmptyArray() {
        assertThat(SpnegoTokenHandler.isSpnegoToken(new byte[0])).isFalse();
    }

    @Test
    void testIsSpnegoTokenWithSingleByte() {
        assertThat(SpnegoTokenHandler.isSpnegoToken(new byte[]{0x60})).isFalse();
    }

    @Test
    void testIsSpnegoTokenWithRandomBytes() {
        assertThat(SpnegoTokenHandler.isSpnegoToken(new byte[]{0x01, 0x02, 0x03})).isFalse();
    }

    @Test
    void testEncodeBase64() {
        byte[] data = new byte[]{1, 2, 3, 4};
        String encoded = SpnegoTokenHandler.encodeBase64(data);
        assertThat(encoded).isEqualTo(Base64.getEncoder().encodeToString(data));
    }

    @Test
    void testDecodeBase64() {
        byte[] original = new byte[]{10, 20, 30};
        String encoded = Base64.getEncoder().encodeToString(original);
        byte[] decoded = SpnegoTokenHandler.decodeBase64(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBase64RoundTrip() {
        byte[] data = new byte[]{(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
        String encoded = SpnegoTokenHandler.encodeBase64(data);
        byte[] decoded = SpnegoTokenHandler.decodeBase64(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testEncodeBase64NullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.encodeBase64(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDecodeBase64NullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.decodeBase64(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEncodeBase64EmptyArray() {
        assertThat(SpnegoTokenHandler.encodeBase64(new byte[0])).isEmpty();
    }

    @Test
    void testDecodeBase64EmptyString() {
        assertThat(SpnegoTokenHandler.decodeBase64("")).isEmpty();
    }

    @Test
    void testExtractMechTokenFromNegTokenInit() throws GssException {
        byte[] mechToken = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        byte[] spnegoToken = SpnegoTokenHandler.createNegTokenInit(mechToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnegoToken);
        assertThat(extracted).isEqualTo(mechToken);
    }

    @Test
    void testExtractMechTokenNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.extractMechToken(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractMechTokenTooShortThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.extractMechToken(new byte[]{0x60}))
                .isInstanceOf(GssException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void testNegTokenInitRoundTrip() throws GssException {
        byte[] original = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] spnego = SpnegoTokenHandler.createNegTokenInit(original);
        assertThat(SpnegoTokenHandler.isSpnegoToken(spnego)).isTrue();
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnego);
        assertThat(extracted).isEqualTo(original);
    }

    @Test
    void testDerLengthShort() {
        // length < 128 should be 1 byte
        assertThat(SpnegoTokenHandler.derLengthSize(50)).isEqualTo(1);
    }

    @Test
    void testDerLengthMedium() {
        // 128 <= length < 256 should be 2 bytes
        assertThat(SpnegoTokenHandler.derLengthSize(200)).isEqualTo(2);
    }

    @Test
    void testDerLengthLong() {
        // 256 <= length < 65536 should be 3 bytes
        assertThat(SpnegoTokenHandler.derLengthSize(1000)).isEqualTo(3);
    }

    // ---- Helpers ----

    private boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
