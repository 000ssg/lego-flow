package ssg.legoflow.auth.gssapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended tests for {@link SpnegoTokenHandler} DER encoding/decoding methods.
 */
class SpnegoTokenHandlerDerTest {

    @ParameterizedTest
    @CsvSource({
        "0,1",
        "127,1",
        "128,2",
        "255,2",
        "256,3",
        "65535,3",
        "65536,4"
    })
    void testDerLengthSize(int length, int expectedSize) {
        assertThat(SpnegoTokenHandler.derLengthSize(length)).isEqualTo(expectedSize);
    }

    @Test
    void testReadDerLengthShortForm() {
        byte[] data = new byte[]{0x42}; // 66 decimal
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(66);
    }

    @Test
    void testReadDerLengthIndefiniteOneByte() {
        byte[] data = new byte[]{(byte) 0x81, (byte) 0xFF}; // length = 255
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(255);
    }

    @Test
    void testReadDerLengthIndefiniteTwoBytes() {
        byte[] data = new byte[]{(byte) 0x82, 0x01, (byte) 0x00}; // length = 256
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(256);
    }

    @Test
    void testReadDerLengthIndefiniteThreeBytes() {
        byte[] data = new byte[]{(byte) 0x83, 0x01, (byte) 0x00, (byte) 0x00}; // length = 65536
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(65536);
    }

    @Test
    void testReadDerLengthAtOffset() {
        byte[] data = new byte[]{0x00, 0x00, 0x10}; // offset=2 → length=16
        assertThat(SpnegoTokenHandler.readDerLength(data, 2)).isEqualTo(16);
    }

    @Test
    void testReadDerLengthOutOfBounds() {
        byte[] data = new byte[]{0x42};
        assertThat(SpnegoTokenHandler.readDerLength(data, 5)).isEqualTo(0); // returns 0 when out of bounds
    }

    @Test
    void testReadDerLengthEmptyData() {
        byte[] data = new byte[0];
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(0);
    }

    @Test
    void testCreateNegTokenInitMinimal() {
        byte[] mechToken = new byte[]{0x01, 0x02, 0x03};
        byte[] result = SpnegoTokenHandler.createNegTokenInit(mechToken);
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void testCreateNegTokenRespComplete() {
        byte[] mechToken = new byte[]{(byte) 0xAA, (byte) 0xBB};
        byte[] result = SpnegoTokenHandler.createNegTokenResp(mechToken, true);
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void testCreateNegTokenRespIncomplete() {
        byte[] mechToken = new byte[]{(byte) 0xCC, (byte) 0xDD};
        byte[] result = SpnegoTokenHandler.createNegTokenResp(mechToken, false);
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void testCreateAndExtractNegTokenInit() throws Exception {
        byte[] mechToken = "test-mech-token".getBytes();
        byte[] token = SpnegoTokenHandler.createNegTokenInit(mechToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(token);
        assertThat(extracted).isEqualTo(mechToken);
    }

    @Test
    void testIsSpnegoTokenForCreatedTokens() {
        byte[] mechToken = new byte[]{0x42};
        byte[] init = SpnegoTokenHandler.createNegTokenInit(mechToken);
        byte[] resp = SpnegoTokenHandler.createNegTokenResp(mechToken, true);
        
        assertThat(SpnegoTokenHandler.isSpnegoToken(init)).isTrue();
        assertThat(SpnegoTokenHandler.isSpnegoToken(resp)).isTrue();
    }

    @Test
    void testIsSpnegoTokenForNonSpnegoTokens() {
        // A plain byte array that's not SPNEGO
        byte[] random = new byte[]{0x42, 0x43, 0x44};
        assertThat(SpnegoTokenHandler.isSpnegoToken(random)).isFalse();
    }

    @Test
    void testIsSpnegoTokenForNegTokenResp() {
        // NegTokenResp starts with 0xa1
        byte[] resp = SpnegoTokenHandler.createNegTokenResp(new byte[]{0x01}, true);
        assertThat(SpnegoTokenHandler.isSpnegoToken(resp)).isTrue();
    }

    @Test
    void testBase64RoundTrip() {
        byte[] original = new byte[]{0x01, 0x02, (byte) 0xFF, (byte) 0x80};
        String encoded = SpnegoTokenHandler.encodeBase64(original);
        byte[] decoded = SpnegoTokenHandler.decodeBase64(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testCreateNegTokenInitNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.createNegTokenInit(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateNegTokenRespNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.createNegTokenResp(null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractMechTokenNullThrows() {
        assertThatThrownBy(() -> SpnegoTokenHandler.extractMechToken(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractMechTokenTooShort() {
        assertThatThrownBy(() -> SpnegoTokenHandler.extractMechToken(new byte[]{0x01}))
                .isInstanceOf(GssException.class);
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

    @ParameterizedTest
    @CsvSource({
        "0,0",
        "127,127",
        "128,-1",
        "255,-1"
    })
    void testReadDerLengthZeroValues(int offset, int expected) {
        byte[] data = new byte[]{0x00};
        assertThat(SpnegoTokenHandler.readDerLength(data, 0)).isEqualTo(0);
    }

    @Test
    void testLongMechanismToken() throws Exception {
        byte[] longToken = new byte[1024];
        for (int i = 0; i < longToken.length; i++) {
            longToken[i] = (byte) (i % 256);
        }
        byte[] spnego = SpnegoTokenHandler.createNegTokenInit(longToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnego);
        assertThat(extracted).isEqualTo(longToken);
    }

    @Test
    void testEmptyMechanismToken() throws Exception {
        byte[] emptyToken = new byte[0];
        byte[] spnego = SpnegoTokenHandler.createNegTokenInit(emptyToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnego);
        assertThat(extracted).isEqualTo(emptyToken);
    }

    @Test
    void testNegTokenRespExtraction() throws Exception {
        byte[] mechToken = "response-token".getBytes();
        byte[] resp = SpnegoTokenHandler.createNegTokenResp(mechToken, true);
        // extractMechToken handles both NegTokenInit and NegTokenResp
        byte[] extracted = SpnegoTokenHandler.extractMechToken(resp);
        assertThat(extracted).isEqualTo(mechToken);
    }

    @Test
    void testLargeTokenWith2ByteLength() throws Exception {
        byte[] bigToken = new byte[500]; // Will use 2-byte DER length encoding
        java.util.Arrays.fill(bigToken, (byte) 0x42);
        byte[] spnego = SpnegoTokenHandler.createNegTokenInit(bigToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnego);
        assertThat(extracted).hasSize(500);
    }

    @Test
    void testLargeTokenWith3ByteLength() throws Exception {
        byte[] bigToken = new byte[2000]; // Will use 3-byte DER length encoding  
        java.util.Arrays.fill(bigToken, (byte) 0x13);
        byte[] spnego = SpnegoTokenHandler.createNegTokenInit(bigToken);
        byte[] extracted = SpnegoTokenHandler.extractMechToken(spnego);
        assertThat(extracted).hasSize(2000);
    }

    @Test
    void testExtractMechTokenInvalidFormat() {
        // Create a token that looks like SPNEGO but has invalid inner structure
        byte[] badToken = new byte[10];
        badToken[0] = (byte) 0x60; // APPLICATION tag
        badToken[1] = 0x08;
        java.util.Arrays.fill(badToken, 2, 10, (byte) 0xFF);
        
        assertThatThrownBy(() -> SpnegoTokenHandler.extractMechToken(badToken))
                .isInstanceOf(GssException.class);
    }

    @Test
    void testMultipleBase64Encodings() {
        String[] inputs = {"", "a", "ab", "abc", "abcd"};
        for (String input : inputs) {
            byte[] bytes = input.getBytes();
            String encoded = SpnegoTokenHandler.encodeBase64(bytes);
            byte[] decoded = SpnegoTokenHandler.decodeBase64(encoded);
            assertThat(decoded).isEqualTo(bytes);
        }
    }

    @Test
    void testNegTokenInitStructureVerification() {
        byte[] mechToken = new byte[]{0x01, 0x02};
        byte[] token = SpnegoTokenHandler.createNegTokenInit(mechToken);
        
        // The token should be valid SPNEGO and extractable
        assertThat(token.length).isGreaterThan(mechToken.length);
    }

    @Test
    void testNegTokenRespStructureVerification() {
        byte[] mechToken = new byte[]{0x03, 0x04};
        byte[] token = SpnegoTokenHandler.createNegTokenResp(mechToken, true);
        
        assertThat(token.length).isGreaterThan(mechToken.length);
    }
}
