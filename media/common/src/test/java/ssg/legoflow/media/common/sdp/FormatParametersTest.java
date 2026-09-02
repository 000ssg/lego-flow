package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FormatParametersTest {

    @Test
    void testParseH264Fmtp() {
        FormatParameters fp = FormatParameters.parse("96 profile-level-id=42e01f;packetization-mode=1");

        assertThat(fp.payloadType()).isEqualTo(96);
        assertThat(fp.parameters()).containsEntry("profile-level-id", "42e01f");
        assertThat(fp.parameters()).containsEntry("packetization-mode", "1");
    }

    @Test
    void testParseNoParams() {
        FormatParameters fp = FormatParameters.parse("96");

        assertThat(fp.payloadType()).isEqualTo(96);
        assertThat(fp.parameters()).isEmpty();
    }

    @Test
    void testParseSingleParam() {
        FormatParameters fp = FormatParameters.parse("97 apt=96");

        assertThat(fp.payloadType()).isEqualTo(97);
        assertThat(fp.parameters()).containsEntry("apt", "96");
    }

    @Test
    void testFormat() {
        FormatParameters fp = FormatParameters.parse("96 profile-level-id=42e01f;packetization-mode=1");

        assertThat(fp.format()).isEqualTo("96 profile-level-id=42e01f;packetization-mode=1");
    }

    @Test
    void testRawValuePreserved() {
        String raw = "profile-level-id=42e01f;packetization-mode=1";
        FormatParameters fp = FormatParameters.parse("96 " + raw);

        assertThat(fp.rawValue()).isEqualTo(raw);
    }

    @Test
    void testToString() {
        FormatParameters fp = FormatParameters.parse("96 mode=20");

        assertThat(fp.toString()).isEqualTo("a=fmtp:96 mode=20");
    }

    @Test
    void testParameterWithoutValue() {
        FormatParameters fp = FormatParameters.parse("96 octet-align");

        assertThat(fp.parameters()).containsEntry("octet-align", "");
    }
}
