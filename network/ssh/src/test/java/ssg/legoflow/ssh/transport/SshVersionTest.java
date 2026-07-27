package ssg.legoflow.ssh.transport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SshVersion}.
 */
class SshVersionTest {

    @Test
    void testDefaultVersion() {
        SshVersion v = SshVersion.defaultVersion();
        assertThat(v.protocolVersion()).isEqualTo("2.0");
        assertThat(v.softwareVersion()).isEqualTo("legoflow_1.0");
        assertThat(v.comments()).isNull();
    }

    @Test
    void testFormat() {
        SshVersion v = new SshVersion("2.0", "legoflow_1.0", null);
        assertThat(v.format()).isEqualTo("SSH-2.0-legoflow_1.0");
    }

    @Test
    void testFormatWithComments() {
        SshVersion v = new SshVersion("2.0", "legoflow_1.0", "test comment");
        assertThat(v.format()).isEqualTo("SSH-2.0-legoflow_1.0 test comment");
    }

    @Test
    void testParse() {
        SshVersion v = SshVersion.parse("SSH-2.0-OpenSSH_8.9p1");
        assertThat(v.protocolVersion()).isEqualTo("2.0");
        assertThat(v.softwareVersion()).isEqualTo("OpenSSH_8.9p1");
        assertThat(v.comments()).isNull();
    }

    @Test
    void testParseWithComments() {
        SshVersion v = SshVersion.parse("SSH-2.0-OpenSSH_8.9p1 Ubuntu-3ubuntu0.1");
        assertThat(v.protocolVersion()).isEqualTo("2.0");
        assertThat(v.softwareVersion()).isEqualTo("OpenSSH_8.9p1");
        assertThat(v.comments()).isEqualTo("Ubuntu-3ubuntu0.1");
    }

    @Test
    void testIsCompatible20() {
        SshVersion v = new SshVersion("2.0", "test", null);
        assertThat(v.isCompatible()).isTrue();
    }

    @Test
    void testIsCompatible199() {
        SshVersion v = new SshVersion("1.99", "test", null);
        assertThat(v.isCompatible()).isTrue();
    }

    @Test
    void testIsNotCompatible10() {
        SshVersion v = new SshVersion("1.0", "test", null);
        assertThat(v.isCompatible()).isFalse();
    }

    @Test
    void testParseInvalidNoPrefix() {
        assertThatThrownBy(() -> SshVersion.parse("NOT-SSH"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseInvalidNoDash() {
        assertThatThrownBy(() -> SshVersion.parse("SSH-2.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToBytes() {
        SshVersion v = SshVersion.defaultVersion();
        byte[] bytes = v.toBytes();
        String str = new String(bytes);
        assertThat(str).isEqualTo("SSH-2.0-legoflow_1.0\r\n");
    }

    @Test
    void testRoundTrip() {
        SshVersion original = new SshVersion("2.0", "my_server_1.0", "extra info");
        SshVersion parsed = SshVersion.parse(original.format());
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void testEqualsAndHashCode() {
        SshVersion v1 = new SshVersion("2.0", "test", null);
        SshVersion v2 = new SshVersion("2.0", "test", null);
        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    }

    @Test
    void testNotEqual() {
        SshVersion v1 = new SshVersion("2.0", "test1", null);
        SshVersion v2 = new SshVersion("2.0", "test2", null);
        assertThat(v1).isNotEqualTo(v2);
    }

    @Test
    void testVersionStringConstant() {
        assertThat(SshVersion.VERSION_STRING).isEqualTo("SSH-2.0-legoflow_1.0");
    }
}
