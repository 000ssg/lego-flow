package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class AttributeTest {

    @Test
    void testParsePropertyAttribute() {
        Attribute a = Attribute.parse("sendrecv");

        assertThat(a.name()).isEqualTo("sendrecv");
        assertThat(a.value()).isEmpty();
    }

    @Test
    void testParseValueAttribute() {
        Attribute a = Attribute.parse("rtpmap:96 H264/90000");

        assertThat(a.name()).isEqualTo("rtpmap");
        assertThat(a.value()).hasValue("96 H264/90000");
    }

    @Test
    void testPropertyFactory() {
        Attribute a = Attribute.property("recvonly");

        assertThat(a.name()).isEqualTo("recvonly");
        assertThat(a.value()).isEmpty();
    }

    @Test
    void testOfFactory() {
        Attribute a = Attribute.of("mid", "0");

        assertThat(a.name()).isEqualTo("mid");
        assertThat(a.value()).hasValue("0");
    }

    @Test
    void testFormatProperty() {
        Attribute a = Attribute.property("sendonly");

        assertThat(a.format()).isEqualTo("sendonly");
    }

    @Test
    void testFormatValue() {
        Attribute a = Attribute.of("rtpmap", "96 H264/90000");

        assertThat(a.format()).isEqualTo("rtpmap:96 H264/90000");
    }

    @Test
    void testToString() {
        Attribute a = Attribute.of("ice-ufrag", "abc123");

        assertThat(a.toString()).isEqualTo("a=ice-ufrag:abc123");
    }

    @Test
    void testParseColonInValue() {
        Attribute a = Attribute.parse("fingerprint:sha-256 AB:CD:EF");

        assertThat(a.name()).isEqualTo("fingerprint");
        assertThat(a.value()).hasValue("sha-256 AB:CD:EF");
    }

    @Test
    void testNullName() {
        assertThatThrownBy(() -> Attribute.property(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEquality() {
        Attribute a1 = Attribute.of("rtpmap", "96 H264/90000");
        Attribute a2 = new Attribute("rtpmap", Optional.of("96 H264/90000"));

        assertThat(a1).isEqualTo(a2);
    }
}
