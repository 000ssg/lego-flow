package ssg.legoflow.http.websocket;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WebSocketCloseCodeTest {

    @Test
    void testNormalClosureCode() {
        assertThat(WebSocketCloseCode.NORMAL_CLOSURE.code()).isEqualTo(1000);
        assertThat(WebSocketCloseCode.NORMAL_CLOSURE.reason()).isEqualTo("Normal Closure");
    }

    @Test
    void testGoingAwayCode() {
        assertThat(WebSocketCloseCode.GOING_AWAY.code()).isEqualTo(1001);
    }

    @Test
    void testProtocolErrorCode() {
        assertThat(WebSocketCloseCode.PROTOCOL_ERROR.code()).isEqualTo(1002);
    }

    @Test
    void testFromCodeValid() {
        assertThat(WebSocketCloseCode.fromCode(1000)).isEqualTo(WebSocketCloseCode.NORMAL_CLOSURE);
        assertThat(WebSocketCloseCode.fromCode(1001)).isEqualTo(WebSocketCloseCode.GOING_AWAY);
        assertThat(WebSocketCloseCode.fromCode(1011)).isEqualTo(WebSocketCloseCode.INTERNAL_ERROR);
    }

    @Test
    void testFromCodeInvalid() {
        assertThatThrownBy(() -> WebSocketCloseCode.fromCode(9999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIsValidCodeStandard() {
        assertThat(WebSocketCloseCode.isValidCode(1000)).isTrue();
        assertThat(WebSocketCloseCode.isValidCode(1001)).isTrue();
        assertThat(WebSocketCloseCode.isValidCode(1011)).isTrue();
    }

    @Test
    void testIsValidCodeReserved() {
        // 1004 is reserved and not valid
        assertThat(WebSocketCloseCode.isValidCode(1004)).isFalse();
        // 1012-1014 are not in the standard range
        assertThat(WebSocketCloseCode.isValidCode(1012)).isFalse();
    }

    @Test
    void testIsValidCodeLibraryRange() {
        assertThat(WebSocketCloseCode.isValidCode(3000)).isTrue();
        assertThat(WebSocketCloseCode.isValidCode(3999)).isTrue();
    }

    @Test
    void testIsValidCodePrivateRange() {
        assertThat(WebSocketCloseCode.isValidCode(4000)).isTrue();
        assertThat(WebSocketCloseCode.isValidCode(4999)).isTrue();
    }

    @Test
    void testIsValidCodeOutOfRange() {
        assertThat(WebSocketCloseCode.isValidCode(999)).isFalse();
        assertThat(WebSocketCloseCode.isValidCode(5000)).isFalse();
        assertThat(WebSocketCloseCode.isValidCode(0)).isFalse();
    }
}
