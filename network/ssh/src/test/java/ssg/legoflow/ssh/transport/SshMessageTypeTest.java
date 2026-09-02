package ssg.legoflow.ssh.transport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SshMessageType}.
 */
class SshMessageTypeTest {

    @Test
    void testDisconnectCode() {
        assertThat(SshMessageType.SSH_MSG_DISCONNECT.code()).isEqualTo(1);
    }

    @Test
    void testKexInitCode() {
        assertThat(SshMessageType.SSH_MSG_KEXINIT.code()).isEqualTo(20);
    }

    @Test
    void testNewKeysCode() {
        assertThat(SshMessageType.SSH_MSG_NEWKEYS.code()).isEqualTo(21);
    }

    @Test
    void testUserAuthRequestCode() {
        assertThat(SshMessageType.SSH_MSG_USERAUTH_REQUEST.code()).isEqualTo(50);
    }

    @Test
    void testChannelOpenCode() {
        assertThat(SshMessageType.SSH_MSG_CHANNEL_OPEN.code()).isEqualTo(90);
    }

    @Test
    void testChannelDataCode() {
        assertThat(SshMessageType.SSH_MSG_CHANNEL_DATA.code()).isEqualTo(94);
    }

    @Test
    void testFromCodeDisconnect() {
        assertThat(SshMessageType.fromCode(1)).isEqualTo(SshMessageType.SSH_MSG_DISCONNECT);
    }

    @Test
    void testFromCodeKexInit() {
        assertThat(SshMessageType.fromCode(20)).isEqualTo(SshMessageType.SSH_MSG_KEXINIT);
    }

    @Test
    void testFromCodeUnknown() {
        assertThatThrownBy(() -> SshMessageType.fromCode(255))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testServiceRequestCode() {
        assertThat(SshMessageType.SSH_MSG_SERVICE_REQUEST.code()).isEqualTo(5);
    }

    @Test
    void testGlobalRequestCode() {
        assertThat(SshMessageType.SSH_MSG_GLOBAL_REQUEST.code()).isEqualTo(80);
    }

    @Test
    void testChannelCloseCode() {
        assertThat(SshMessageType.SSH_MSG_CHANNEL_CLOSE.code()).isEqualTo(97);
    }
}
