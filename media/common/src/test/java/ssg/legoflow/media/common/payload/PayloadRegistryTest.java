package ssg.legoflow.media.common.payload;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.*;

class PayloadRegistryTest {

    @Test
    void testLookupStaticPcmu() {
        var registry = new PayloadRegistry();

        assertThat(registry.lookup(0)).isPresent();
        assertThat(registry.lookup(0).get().codec()).isEqualTo("PCMU");
    }

    @Test
    void testLookupStaticPcma() {
        var registry = new PayloadRegistry();

        assertThat(registry.lookup(8)).isPresent();
        assertThat(registry.lookup(8).get().codec()).isEqualTo("PCMA");
    }

    @Test
    void testLookupUnknownStatic() {
        var registry = new PayloadRegistry();

        assertThat(registry.lookup(1)).isEmpty();
    }

    @Test
    void testRegisterDynamic() {
        var registry = new PayloadRegistry();
        registry.registerDynamic(96, "opus", 48000, OptionalInt.of(2), "audio");

        assertThat(registry.lookup(96)).isPresent();
        assertThat(registry.lookup(96).get().codec()).isEqualTo("opus");
        assertThat(registry.lookup(96).get().clockRate()).isEqualTo(48000);
    }

    @Test
    void testDynamicOverridesStatic() {
        var registry = new PayloadRegistry();
        // Register a dynamic type that happens to have a number in static range — not allowed
        assertThatThrownBy(() -> registry.registerDynamic(0, "X", 8000,
                OptionalInt.of(1), "audio"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testClearDynamic() {
        var registry = new PayloadRegistry();
        registry.registerDynamic(96, "opus", 48000, OptionalInt.of(2), "audio");
        registry.clearDynamic();

        assertThat(registry.lookup(96)).isEmpty();
        assertThat(registry.dynamicTypes()).isEmpty();
    }

    @Test
    void testStaticTypesContainsKnown() {
        var statics = PayloadRegistry.staticTypes();

        assertThat(statics).containsKey(0);  // PCMU
        assertThat(statics).containsKey(8);  // PCMA
        assertThat(statics).containsKey(18); // G729
        assertThat(statics).containsKey(26); // JPEG
        assertThat(statics).containsKey(34); // H263
    }

    @Test
    void testStaticTypesUnmodifiable() {
        assertThatThrownBy(() -> PayloadRegistry.staticTypes().put(99, PayloadType.PCMU))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testDynamicTypesMap() {
        var registry = new PayloadRegistry();
        registry.registerDynamic(96, "opus", 48000, OptionalInt.of(2), "audio");
        registry.registerDynamic(97, "H264", 90000, OptionalInt.empty(), "video");

        assertThat(registry.dynamicTypes()).hasSize(2);
        assertThat(registry.dynamicTypes()).containsKey(96);
        assertThat(registry.dynamicTypes()).containsKey(97);
    }

    @Test
    void testRegisterDynamicReturnsPayloadType() {
        var registry = new PayloadRegistry();
        PayloadType pt = registry.registerDynamic(96, "VP8", 90000,
                OptionalInt.empty(), "video");

        assertThat(pt.number()).isEqualTo(96);
        assertThat(pt.codec()).isEqualTo("VP8");
        assertThat(pt.isDynamic()).isTrue();
    }
}
