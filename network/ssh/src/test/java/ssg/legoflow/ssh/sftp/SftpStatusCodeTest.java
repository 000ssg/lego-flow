package ssg.legoflow.ssh.sftp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SftpStatusCode}.
 */
class SftpStatusCodeTest {

    @Test void testAllStatusCodes() {
        var values = SftpStatusCode.values();
        assertThat(values).hasSize(9);
    }

    @Test void testOxCode() {
        assertThat(SftpStatusCode.SSH_FX_OK.code()).isEqualTo(0);
        assertThat(SftpStatusCode.SSH_FX_OK.description()).isEqualTo("Success");
    }

    @Test void testEofCode() {
        assertThat(SftpStatusCode.SSH_FX_EOF.code()).isEqualTo(1);
    }

    @Test void testNoSuchFileCode() {
        assertThat(SftpStatusCode.SSH_FX_NO_SUCH_FILE.code()).isEqualTo(2);
    }

    @Test void testPermissionDeniedCode() {
        assertThat(SftpStatusCode.SSH_FX_PERMISSION_DENIED.code()).isEqualTo(3);
    }

    @Test void testFailureCode() {
        assertThat(SftpStatusCode.SSH_FX_FAILURE.code()).isEqualTo(4);
    }

    @Test void testBadMessageCode() {
        assertThat(SftpStatusCode.SSH_FX_BAD_MESSAGE.code()).isEqualTo(5);
    }

    @Test void testNoConnectionCode() {
        assertThat(SftpStatusCode.SSH_FX_NO_CONNECTION.code()).isEqualTo(6);
    }

    @Test void testConnectionLostCode() {
        assertThat(SftpStatusCode.SSH_FX_CONNECTION_LOST.code()).isEqualTo(7);
    }

    @Test void testOpUnsupportedCode() {
        assertThat(SftpStatusCode.SSH_FX_OP_UNSUPPORTED.code()).isEqualTo(8);
    }

    @Test void testFromCodeValid() {
        for (var sc : SftpStatusCode.values()) {
            assertThat(SftpStatusCode.fromCode(sc.code())).isEqualTo(sc);
        }
    }

    @Test void testFromCodeUnknownThrows() {
        assertThatThrownBy(() -> SftpStatusCode.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown SFTP status code");
    }

    @Test void testDistinctCodes() {
        var codes = java.util.Arrays.stream(SftpStatusCode.values())
                .mapToInt(SftpStatusCode::code).boxed().collect(java.util.stream.Collectors.toSet());
        assertThat(codes.size()).isEqualTo(SftpStatusCode.values().length);
    }

    @Test void testDescriptionsNotBlank() {
        for (var sc : SftpStatusCode.values()) {
            assertThat(sc.description()).isNotBlank();
        }
    }
}
