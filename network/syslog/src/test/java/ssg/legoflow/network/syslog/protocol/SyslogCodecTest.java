package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link SyslogCodec}.
 */
class SyslogCodecTest {

    @Test
    void testEncodeMinimalMessage() {
        var msg = SyslogMessage.builder(Facility.USER, Severity.NOTICE).build();
        String encoded = SyslogCodec.encode(msg);
        assertThat(encoded).startsWith("<13>1 ");
        assertThat(encoded).contains("- - - - -");
    }

    @Test
    void testEncodeFullMessage() {
        var msg = SyslogMessage.builder(Facility.LOCAL0, Severity.INFO)
                .timestamp(Instant.parse("2024-01-15T10:30:00Z"))
                .hostname("myhost")
                .appName("myapp")
                .procId("1234")
                .msgId("ID001")
                .message("Test message")
                .build();
        String encoded = SyslogCodec.encode(msg);
        assertThat(encoded).startsWith("<134>1 ");
        assertThat(encoded).contains("myhost");
        assertThat(encoded).contains("myapp");
        assertThat(encoded).contains("1234");
        assertThat(encoded).contains("ID001");
        assertThat(encoded).endsWith("Test message");
    }

    @Test
    void testEncodeWithStructuredData() {
        var sd = StructuredData.builder("exampleSDID@32473")
                .param("iut", "3")
                .param("eventSource", "Application")
                .build();
        var msg = SyslogMessage.builder(Facility.LOCAL0, Severity.INFO)
                .structuredData(List.of(sd))
                .message("Test")
                .build();
        String encoded = SyslogCodec.encode(msg);
        assertThat(encoded).contains("[exampleSDID@32473");
        assertThat(encoded).contains("iut=\"3\"");
    }

    @Test
    void testDecodeMinimalMessage() {
        String text = "<13>1 - - - - - -";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.facility()).isEqualTo(Facility.USER);
        assertThat(msg.severity()).isEqualTo(Severity.NOTICE);
        assertThat(msg.timestamp()).isNull();
        assertThat(msg.hostname()).isNull();
        assertThat(msg.appName()).isNull();
        assertThat(msg.procId()).isNull();
        assertThat(msg.msgId()).isNull();
        assertThat(msg.structuredData()).isEmpty();
        assertThat(msg.message()).isNull();
    }

    @Test
    void testDecodeFullMessage() {
        String text = "<134>1 2024-01-15T10:30:00+00:00 myhost myapp 1234 ID001 - Test message";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.facility()).isEqualTo(Facility.LOCAL0);
        assertThat(msg.severity()).isEqualTo(Severity.INFO);
        assertThat(msg.hostname()).isEqualTo("myhost");
        assertThat(msg.appName()).isEqualTo("myapp");
        assertThat(msg.procId()).isEqualTo("1234");
        assertThat(msg.msgId()).isEqualTo("ID001");
        assertThat(msg.message()).isEqualTo("Test message");
    }

    @Test
    void testDecodeWithStructuredData() {
        String text = "<134>1 - - - - - [exampleSDID@32473 iut=\"3\" eventSource=\"Application\"] Test";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.structuredData()).hasSize(1);
        var sd = msg.structuredData().getFirst();
        assertThat(sd.id()).isEqualTo("exampleSDID@32473");
        assertThat(sd.params().get("iut")).isEqualTo("3");
        assertThat(sd.params().get("eventSource")).isEqualTo("Application");
        assertThat(msg.message()).isEqualTo("Test");
    }

    @Test
    void testDecodeMultipleStructuredDataElements() {
        String text = "<134>1 - - - - - [sd1 k1=\"v1\"][sd2 k2=\"v2\"] msg";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.structuredData()).hasSize(2);
        assertThat(msg.structuredData().get(0).id()).isEqualTo("sd1");
        assertThat(msg.structuredData().get(1).id()).isEqualTo("sd2");
    }

    @Test
    void testDecodeEscapedValues() {
        String text = "<134>1 - - - - - [test msg=\"has \\\" and \\] and \\\\\"] done";
        SyslogMessage msg = SyslogCodec.decode(text);
        var sd = msg.structuredData().getFirst();
        assertThat(sd.params().get("msg")).isEqualTo("has \" and ] and \\");
    }

    @Test
    void testRoundTrip() {
        var original = SyslogMessage.builder(Facility.AUTH, Severity.WARNING)
                .timestamp(Instant.parse("2024-06-01T12:00:00Z"))
                .hostname("server01")
                .appName("sshd")
                .procId("5678")
                .msgId("LOGIN")
                .structuredData(List.of(
                        StructuredData.builder("origin")
                                .param("ip", "192.168.1.1")
                                .build()))
                .message("Failed login attempt")
                .build();

        String encoded = SyslogCodec.encode(original);
        SyslogMessage decoded = SyslogCodec.decode(encoded);

        assertThat(decoded.facility()).isEqualTo(original.facility());
        assertThat(decoded.severity()).isEqualTo(original.severity());
        assertThat(decoded.hostname()).isEqualTo(original.hostname());
        assertThat(decoded.appName()).isEqualTo(original.appName());
        assertThat(decoded.procId()).isEqualTo(original.procId());
        assertThat(decoded.msgId()).isEqualTo(original.msgId());
        assertThat(decoded.message()).isEqualTo(original.message());
        assertThat(decoded.structuredData()).hasSize(1);
        assertThat(decoded.structuredData().getFirst().params().get("ip"))
                .isEqualTo("192.168.1.1");
    }

    @Test
    void testRoundTripNilValues() {
        var original = SyslogMessage.builder(Facility.KERN, Severity.EMERGENCY).build();
        String encoded = SyslogCodec.encode(original);
        SyslogMessage decoded = SyslogCodec.decode(encoded);
        assertThat(decoded.facility()).isEqualTo(Facility.KERN);
        assertThat(decoded.severity()).isEqualTo(Severity.EMERGENCY);
        assertThat(decoded.timestamp()).isNull();
        assertThat(decoded.hostname()).isNull();
    }

    @Test
    void testDecodeBytesRoundTrip() {
        var msg = SyslogMessage.builder(Facility.DAEMON, Severity.ERROR)
                .hostname("test")
                .message("byte test")
                .build();
        byte[] bytes = SyslogCodec.encodeToBytes(msg);
        SyslogMessage decoded = SyslogCodec.decode(bytes);
        assertThat(decoded.facility()).isEqualTo(Facility.DAEMON);
        assertThat(decoded.message()).isEqualTo("byte test");
    }

    @Test
    void testDecodeNullThrows() {
        assertThatThrownBy(() -> SyslogCodec.decode((String) null))
                .isInstanceOf(SyslogParseException.class);
    }

    @Test
    void testDecodeEmptyThrows() {
        assertThatThrownBy(() -> SyslogCodec.decode(""))
                .isInstanceOf(SyslogParseException.class);
    }

    @Test
    void testDecodeMissingPriThrows() {
        assertThatThrownBy(() -> SyslogCodec.decode("no pri here"))
                .isInstanceOf(SyslogParseException.class);
    }

    @Test
    void testDecodePriOutOfRange() {
        assertThatThrownBy(() -> SyslogCodec.decode("<192>1 - - - - - -"))
                .isInstanceOf(SyslogParseException.class);
    }

    @Test
    void testDecodeUnsupportedVersion() {
        assertThatThrownBy(() -> SyslogCodec.decode("<13>2 - - - - - -"))
                .isInstanceOf(SyslogParseException.class);
    }

    @Test
    void testEncodePriRanges() {
        // PRI 0 = kern.emerg
        var msg0 = SyslogMessage.builder(Facility.KERN, Severity.EMERGENCY).build();
        assertThat(SyslogCodec.encode(msg0)).startsWith("<0>");

        // PRI 191 = local7.debug
        var msg191 = SyslogMessage.builder(Facility.LOCAL7, Severity.DEBUG).build();
        assertThat(SyslogCodec.encode(msg191)).startsWith("<191>");
    }

    @Test
    void testStructuredDataNoMessage() {
        var msg = SyslogMessage.builder(Facility.USER, Severity.INFO)
                .structuredData(List.of(StructuredData.of("test")))
                .build();
        String encoded = SyslogCodec.encode(msg);
        assertThat(encoded).endsWith("[test]");
    }

    @Test
    void testDecodeStructuredDataNoMessage() {
        String text = "<14>1 - - - - - [test]";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.structuredData()).hasSize(1);
        assertThat(msg.message()).isNull();
    }

    @Test
    void testDecodeMessageWithSpaces() {
        String text = "<14>1 - - - - - - This is a message with spaces";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.message()).isEqualTo("This is a message with spaces");
    }

    @Test
    void testDecodeStructuredDataNoParams() {
        String text = "<14>1 - - - - - [myID] msg";
        SyslogMessage msg = SyslogCodec.decode(text);
        assertThat(msg.structuredData()).hasSize(1);
        assertThat(msg.structuredData().getFirst().id()).isEqualTo("myID");
        assertThat(msg.structuredData().getFirst().params()).isEmpty();
    }

    @Test
    void testAllFacilityCodesInPri() {
        for (Facility f : Facility.values()) {
            var msg = SyslogMessage.builder(f, Severity.INFO).build();
            String encoded = SyslogCodec.encode(msg);
            SyslogMessage decoded = SyslogCodec.decode(encoded);
            assertThat(decoded.facility()).isEqualTo(f);
        }
    }

    @Test
    void testAllSeverityCodesInPri() {
        for (Severity s : Severity.values()) {
            var msg = SyslogMessage.builder(Facility.USER, s).build();
            String encoded = SyslogCodec.encode(msg);
            SyslogMessage decoded = SyslogCodec.decode(encoded);
            assertThat(decoded.severity()).isEqualTo(s);
        }
    }
}
