package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MediaTypeTest {

    @Test
    void testAllMediaTypes() {
        assertThat(MediaType.AUDIO.token()).isEqualTo("audio");
        assertThat(MediaType.VIDEO.token()).isEqualTo("video");
        assertThat(MediaType.TEXT.token()).isEqualTo("text");
        assertThat(MediaType.APPLICATION.token()).isEqualTo("application");
        assertThat(MediaType.MESSAGE.token()).isEqualTo("message");
    }

    @Test
    void testFromTokenCaseInsensitive() {
        assertThat(MediaType.fromToken("AUDIO")).isEqualTo(MediaType.AUDIO);
        assertThat(MediaType.fromToken("Video")).isEqualTo(MediaType.VIDEO);
    }

    @Test
    void testFromTokenUnknown() {
        assertThatThrownBy(() -> MediaType.fromToken("xyz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValues() {
        assertThat(MediaType.values()).hasSize(5);
    }
}
