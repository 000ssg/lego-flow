package ssg.legoflow.media.common.payload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PayloadTypeTest {

    @Test
    void testPcmu() {
        assertThat(PayloadType.PCMU.number()).isZero();
        assertThat(PayloadType.PCMU.codec()).isEqualTo("PCMU");
        assertThat(PayloadType.PCMU.clockRate()).isEqualTo(8000);
        assertThat(PayloadType.PCMU.channels()).hasValue(1);
        assertThat(PayloadType.PCMU.mediaType()).isEqualTo("audio");
    }

    @Test
    void testPcma() {
        assertThat(PayloadType.PCMA.number()).isEqualTo(8);
        assertThat(PayloadType.PCMA.codec()).isEqualTo("PCMA");
    }

    @Test
    void testG729() {
        assertThat(PayloadType.G729.number()).isEqualTo(18);
        assertThat(PayloadType.G729.codec()).isEqualTo("G729");
    }

    @Test
    void testH264Video() {
        assertThat(PayloadType.H261.number()).isEqualTo(31);
        assertThat(PayloadType.H261.clockRate()).isEqualTo(90000);
        assertThat(PayloadType.H261.channels()).isEmpty();
        assertThat(PayloadType.H261.mediaType()).isEqualTo("video");
    }

    @Test
    void testStaticVsDynamic() {
        assertThat(PayloadType.PCMU.isStatic()).isTrue();
        assertThat(PayloadType.PCMU.isDynamic()).isFalse();

        var dynamic = new PayloadType(96, "opus", 48000,
                java.util.OptionalInt.of(2), "audio");
        assertThat(dynamic.isDynamic()).isTrue();
        assertThat(dynamic.isStatic()).isFalse();
    }

    @Test
    void testInvalidPayloadNumber() {
        assertThatThrownBy(() -> new PayloadType(128, "X", 8000,
                java.util.OptionalInt.of(1), "audio"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativePayloadNumber() {
        assertThatThrownBy(() -> new PayloadType(-1, "X", 8000,
                java.util.OptionalInt.of(1), "audio"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testL16Stereo() {
        assertThat(PayloadType.L16_STEREO.number()).isEqualTo(10);
        assertThat(PayloadType.L16_STEREO.channels()).hasValue(2);
        assertThat(PayloadType.L16_STEREO.clockRate()).isEqualTo(44100);
    }

    @Test
    void testDynamicRange() {
        assertThat(PayloadType.DYNAMIC_MIN).isEqualTo(96);
        assertThat(PayloadType.MAX).isEqualTo(127);
    }
}
