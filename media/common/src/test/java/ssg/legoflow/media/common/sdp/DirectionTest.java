package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DirectionTest {

    @Test
    void testAllDirections() {
        assertThat(Direction.SENDRECV.token()).isEqualTo("sendrecv");
        assertThat(Direction.SENDONLY.token()).isEqualTo("sendonly");
        assertThat(Direction.RECVONLY.token()).isEqualTo("recvonly");
        assertThat(Direction.INACTIVE.token()).isEqualTo("inactive");
    }

    @Test
    void testFromTokenCaseInsensitive() {
        assertThat(Direction.fromToken("SENDRECV")).isEqualTo(Direction.SENDRECV);
        assertThat(Direction.fromToken("SendOnly")).isEqualTo(Direction.SENDONLY);
        assertThat(Direction.fromToken("recvonly")).isEqualTo(Direction.RECVONLY);
        assertThat(Direction.fromToken("INACTIVE")).isEqualTo(Direction.INACTIVE);
    }

    @Test
    void testFromTokenUnknown() {
        assertThatThrownBy(() -> Direction.fromToken("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        assertThat(Direction.SENDRECV.toString()).isEqualTo("sendrecv");
    }
}
