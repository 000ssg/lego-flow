package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FingerprintTest {

    @Test
    void testParseSha256() {
        Fingerprint fp = Fingerprint.parse(
                "sha-256 AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89");

        assertThat(fp.hashFunction()).isEqualTo("sha-256");
        assertThat(fp.hashValue()).startsWith("AB:CD:EF");
    }

    @Test
    void testParseSha1() {
        Fingerprint fp = Fingerprint.parse("sha-1 AA:BB:CC:DD:EE:FF");

        assertThat(fp.hashFunction()).isEqualTo("sha-1");
        assertThat(fp.hashValue()).isEqualTo("AA:BB:CC:DD:EE:FF");
    }

    @Test
    void testFormat() {
        Fingerprint fp = new Fingerprint("sha-256", "AB:CD:EF");

        assertThat(fp.format()).isEqualTo("sha-256 AB:CD:EF");
    }

    @Test
    void testRoundTrip() {
        String value = "sha-256 AB:CD:EF:01:23";
        Fingerprint fp = Fingerprint.parse(value);

        assertThat(fp.format()).isEqualTo(value);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> Fingerprint.parse("sha-256"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        Fingerprint fp = new Fingerprint("sha-256", "AB:CD");

        assertThat(fp.toString()).isEqualTo("a=fingerprint:sha-256 AB:CD");
    }
}
