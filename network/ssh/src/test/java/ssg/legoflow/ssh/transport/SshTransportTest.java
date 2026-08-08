package ssg.legoflow.ssh.transport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SshTransportTest {

    @Test void testSshMessageTypes() {
        var disconnect = SshMessageType.SSH_MSG_DISCONNECT;
        assertThat(disconnect.code()).isEqualTo(1);
    }

    @Test void testAllMessageTypesHavePositiveCodes() {
        for (var type : SshMessageType.values()) {
            assertThat(type.code()).isGreaterThan(0);
        }
    }

    @Test void testSshVersionParse() {
        var version = SshVersion.parse("SSH-2.0-TestClient_1.0");
        assertThat(version).isNotNull();
    }
}
