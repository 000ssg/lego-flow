package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StompCommand}.
 *
 * @since 0.1.0
 */
class StompCommandTest {

    @Test
    void testClientCommands() {
        assertThat(StompCommand.STOMP.isClientCommand()).isTrue();
        assertThat(StompCommand.CONNECT.isClientCommand()).isTrue();
        assertThat(StompCommand.SEND.isClientCommand()).isTrue();
        assertThat(StompCommand.SUBSCRIBE.isClientCommand()).isTrue();
        assertThat(StompCommand.UNSUBSCRIBE.isClientCommand()).isTrue();
        assertThat(StompCommand.ACK.isClientCommand()).isTrue();
        assertThat(StompCommand.NACK.isClientCommand()).isTrue();
        assertThat(StompCommand.BEGIN.isClientCommand()).isTrue();
        assertThat(StompCommand.COMMIT.isClientCommand()).isTrue();
        assertThat(StompCommand.ABORT.isClientCommand()).isTrue();
        assertThat(StompCommand.DISCONNECT.isClientCommand()).isTrue();
    }

    @Test
    void testServerFrames() {
        assertThat(StompCommand.CONNECTED.isClientCommand()).isFalse();
        assertThat(StompCommand.MESSAGE.isClientCommand()).isFalse();
        assertThat(StompCommand.RECEIPT.isClientCommand()).isFalse();
        assertThat(StompCommand.ERROR.isClientCommand()).isFalse();
    }

    @Test
    void testFromString() {
        assertThat(StompCommand.fromString("CONNECT")).isEqualTo(StompCommand.CONNECT);
        assertThat(StompCommand.fromString("connect")).isEqualTo(StompCommand.CONNECT);
        assertThat(StompCommand.fromString("Send")).isEqualTo(StompCommand.SEND);
    }

    @Test
    void testFromStringUnknown() {
        assertThatThrownBy(() -> StompCommand.fromString("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown STOMP command");
    }

    @Test
    void testAllCommandsPresent() {
        // 11 client commands + 4 server frames + HEARTBEAT = 16
        assertThat(StompCommand.values()).hasSize(16);
    }
}
