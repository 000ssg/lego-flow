package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyslogMessage}.
 */
class SyslogMessageTest {

    @Test
    void testPriCalculation() {
        var msg = SyslogMessage.builder(Facility.KERN, Severity.EMERGENCY)
                .build();
        assertThat(msg.pri()).isEqualTo(0);

        msg = SyslogMessage.builder(Facility.LOCAL0, Severity.DEBUG)
                .build();
        assertThat(msg.pri()).isEqualTo(135);

        msg = SyslogMessage.builder(Facility.AUTH, Severity.WARNING)
                .build();
        assertThat(msg.pri()).isEqualTo(36);
    }

    @Test
    void testVersionConstant() {
        assertThat(SyslogMessage.VERSION).isEqualTo(1);
    }

    @Test
    void testBuilderAllFields() {
        Instant ts = Instant.parse("2024-01-15T10:30:00Z");
        var sd = StructuredData.of("test");
        var msg = SyslogMessage.builder(Facility.DAEMON, Severity.INFO)
                .timestamp(ts)
                .hostname("myhost")
                .appName("myapp")
                .procId("1234")
                .msgId("ID001")
                .structuredData(List.of(sd))
                .message("Hello World")
                .build();

        assertThat(msg.facility()).isEqualTo(Facility.DAEMON);
        assertThat(msg.severity()).isEqualTo(Severity.INFO);
        assertThat(msg.timestamp()).isEqualTo(ts);
        assertThat(msg.hostname()).isEqualTo("myhost");
        assertThat(msg.appName()).isEqualTo("myapp");
        assertThat(msg.procId()).isEqualTo("1234");
        assertThat(msg.msgId()).isEqualTo("ID001");
        assertThat(msg.structuredData()).hasSize(1);
        assertThat(msg.message()).isEqualTo("Hello World");
    }

    @Test
    void testBuilderMinimalFields() {
        var msg = SyslogMessage.builder(Facility.USER, Severity.NOTICE)
                .build();
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
    void testNullFacilityThrows() {
        assertThatThrownBy(() -> new SyslogMessage(null, Severity.INFO,
                null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullSeverityThrows() {
        assertThatThrownBy(() -> new SyslogMessage(Facility.USER, null,
                null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHostnameTooLong() {
        String longHostname = "a".repeat(256);
        assertThatThrownBy(() -> SyslogMessage.builder(Facility.USER, Severity.INFO)
                .hostname(longHostname).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAppNameTooLong() {
        String longAppName = "a".repeat(49);
        assertThatThrownBy(() -> SyslogMessage.builder(Facility.USER, Severity.INFO)
                .appName(longAppName).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testProcIdTooLong() {
        String longProcId = "a".repeat(129);
        assertThatThrownBy(() -> SyslogMessage.builder(Facility.USER, Severity.INFO)
                .procId(longProcId).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMsgIdTooLong() {
        String longMsgId = "a".repeat(33);
        assertThatThrownBy(() -> SyslogMessage.builder(Facility.USER, Severity.INFO)
                .msgId(longMsgId).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testStructuredDataImmutable() {
        var msg = SyslogMessage.builder(Facility.USER, Severity.INFO)
                .structuredData(List.of(StructuredData.of("test")))
                .build();
        assertThatThrownBy(() -> msg.structuredData().add(StructuredData.of("other")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testAllFacilitySeverityCombinations() {
        for (Facility f : Facility.values()) {
            for (Severity s : Severity.values()) {
                var msg = SyslogMessage.builder(f, s).build();
                int expectedPri = f.code() * 8 + s.code();
                assertThat(msg.pri()).isEqualTo(expectedPri);
            }
        }
    }
}
