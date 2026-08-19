package ssg.legoflow.coap.protocol;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link CoapOptionRegistry} covering registry lookup, option metadata,
 * Format enum, and option classification helpers.
 */
class CoapOptionRegistryTest {

    @Test
    void testLookupKnownOptions() {
        assertThat(CoapOptionRegistry.lookup(CoapOption.URI_HOST)).isPresent();
        assertThat(CoapOptionRegistry.lookup(CoapOption.ETAG)).isPresent();
        assertThat(CoapOptionRegistry.lookup(CoapOption.CONTENT_FORMAT)).isPresent();
        assertThat(CoapOptionRegistry.lookup(CoapOption.BLOCK2)).isPresent();
        assertThat(CoapOptionRegistry.lookup(CoapOption.OBSERVE)).isPresent();
    }

    @Test
    void testLookupUnknownOption() {
        assertThat(CoapOptionRegistry.lookup(9999)).isEmpty();
        assertThat(CoapOptionRegistry.lookup(-1)).isEmpty();
    }

    @Test
    void testUriHostMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.URI_HOST).orElseThrow();
        assertThat(info.number()).isEqualTo(CoapOption.URI_HOST);
        assertThat(info.name()).isEqualTo("Uri-Host");
        assertThat(info.critical()).isTrue();
        assertThat(info.unsafe()).isTrue();
        assertThat(info.repeatable()).isFalse();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.STRING);
    }

    @Test
    void testEtagMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.ETAG).orElseThrow();
        assertThat(info.critical()).isFalse();
        assertThat(info.unsafe()).isFalse();
        assertThat(info.repeatable()).isTrue();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.OPAQUE);
    }

    @Test
    void testIfNoneMatchMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.IF_NONE_MATCH).orElseThrow();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.EMPTY);
        assertThat(info.minLength()).isZero();
        assertThat(info.maxLength()).isZero();
    }

    @Test
    void testObserveMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.OBSERVE).orElseThrow();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.UINT);
        assertThat(info.unsafe()).isTrue();
    }

    @Test
    void testUriQueryMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.URI_QUERY).orElseThrow();
        assertThat(info.repeatable()).isTrue();
        assertThat(info.critical()).isTrue();
        assertThat(info.unsafe()).isTrue();
    }

    @Test
    void testProxyUriMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.PROXY_URI).orElseThrow();
        assertThat(info.maxLength()).isEqualTo(1034);
    }

    @Test
    void testSize2IsNoCacheKey() {
        var info = CoapOptionRegistry.lookup(CoapOption.SIZE2).orElseThrow();
        assertThat(info.noCacheKey()).isTrue();
    }

    @Test
    void testContentFormatMetadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.CONTENT_FORMAT).orElseThrow();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.UINT);
        assertThat(info.critical()).isFalse();
    }

    @Test
    void testBlock2Metadata() {
        var info = CoapOptionRegistry.lookup(CoapOption.BLOCK2).orElseThrow();
        assertThat(info.critical()).isTrue();
        assertThat(info.unsafe()).isTrue();
        assertThat(info.repeatable()).isFalse();
    }

    @Test
    void testIsCritical() {
        // Odd numbers are critical
        assertThat(CoapOptionRegistry.isCritical(1)).isTrue();
        assertThat(CoapOptionRegistry.isCritical(3)).isTrue();
        assertThat(CoapOptionRegistry.isCritical(255)).isTrue();
        assertThat(CoapOptionRegistry.isCritical(0)).isFalse();
        assertThat(CoapOptionRegistry.isCritical(4)).isFalse();
    }

    @Test
    void testIsElective() {
        // Even numbers are elective
        assertThat(CoapOptionRegistry.isElective(0)).isTrue();
        assertThat(CoapOptionRegistry.isElective(2)).isTrue();
        assertThat(CoapOptionRegistry.isElective(254)).isTrue();
        assertThat(CoapOptionRegistry.isElective(1)).isFalse();
    }

    @Test
    void testIsUnsafe() {
        // (number & 2) == 2 means unsafe
        assertThat(CoapOptionRegistry.isUnsafe(2)).isTrue();
        assertThat(CoapOptionRegistry.isUnsafe(3)).isTrue();
        assertThat(CoapOptionRegistry.isUnsafe(6)).isTrue();
        assertThat(CoapOptionRegistry.isUnsafe(0)).isFalse();
        assertThat(CoapOptionRegistry.isUnsafe(1)).isFalse();
    }

    @Test
    void testIsSafeToForward() {
        assertThat(CoapOptionRegistry.isSafeToForward(0)).isTrue();
        assertThat(CoapOptionRegistry.isSafeToForward(1)).isTrue();
        assertThat(CoapOptionRegistry.isSafeToForward(2)).isFalse();
        assertThat(CoapOptionRegistry.isSafeToForward(4)).isTrue();
    }

    @Test
    void testIsNoCacheKey() {
        // (number & 0x1E) == 0x1C
        assertThat(CoapOptionRegistry.isNoCacheKey(0x1C)).isTrue();
        assertThat(CoapOptionRegistry.isNoCacheKey(0x1D)).isTrue();
        assertThat(CoapOptionRegistry.isNoCacheKey(0x3C)).isTrue();  // SIZE2 = 60 = 0x3C
        assertThat(CoapOptionRegistry.isNoCacheKey(0)).isFalse();
        assertThat(CoapOptionRegistry.isNoCacheKey(1)).isFalse();
    }

    @Test
    void testRegisterCustomOption() {
        int customNumber = 32768; // User-defined option number
        var info = new CoapOptionRegistry.OptionInfo(
                customNumber, "X-Custom", true, false, false,
                CoapOptionRegistry.Format.OPAQUE, 1, 16, false);
        
        CoapOptionRegistry.register(info);
        assertThat(CoapOptionRegistry.lookup(customNumber)).contains(info);
    }

    @Test
    void testFormatEnumValues() {
        var values = CoapOptionRegistry.Format.values();
        assertThat(values).containsExactly(
                CoapOptionRegistry.Format.EMPTY,
                CoapOptionRegistry.Format.OPAQUE,
                CoapOptionRegistry.Format.UINT,
                CoapOptionRegistry.Format.STRING);
    }

    @Test
    void testAllPreRegisteredOptionsAccessible() {
        int[] optionNumbers = {
            CoapOption.IF_MATCH, CoapOption.URI_HOST, CoapOption.ETAG,
            CoapOption.IF_NONE_MATCH, CoapOption.OBSERVE, CoapOption.URI_PORT,
            CoapOption.LOCATION_PATH, CoapOption.URI_PATH, CoapOption.CONTENT_FORMAT,
            CoapOption.MAX_AGE, CoapOption.URI_QUERY, CoapOption.ACCEPT,
            CoapOption.LOCATION_QUERY, CoapOption.BLOCK2, CoapOption.BLOCK1,
            CoapOption.SIZE2, CoapOption.PROXY_URI, CoapOption.PROXY_SCHEME,
            CoapOption.SIZE1
        };
        
        for (int num : optionNumbers) {
            assertThat(CoapOptionRegistry.lookup(num))
                    .as("Option %d should be registered", num)
                    .isPresent();
        }
    }

    @Test
    void testOptionInfoRecordProperties() {
        var info = new CoapOptionRegistry.OptionInfo(
                99, "Test-Opt", true, false, true,
                CoapOptionRegistry.Format.STRING, 1, 255, true);
        
        assertThat(info.number()).isEqualTo(99);
        assertThat(info.name()).isEqualTo("Test-Opt");
        assertThat(info.critical()).isTrue();
        assertThat(info.unsafe()).isFalse();
        assertThat(info.noCacheKey()).isTrue();
        assertThat(info.format()).isEqualTo(CoapOptionRegistry.Format.STRING);
        assertThat(info.minLength()).isEqualTo(1);
        assertThat(info.maxLength()).isEqualTo(255);
        assertThat(info.repeatable()).isTrue();
    }

    @Test
    void testObserveRepeatableFalse() {
        var info = CoapOptionRegistry.lookup(CoapOption.OBSERVE).orElseThrow();
        assertThat(info.repeatable()).isFalse();
    }

    @Test
    void testIfMatchRepeatableTrue() {
        var info = CoapOptionRegistry.lookup(CoapOption.IF_MATCH).orElseThrow();
        assertThat(info.repeatable()).isTrue();
    }
}
