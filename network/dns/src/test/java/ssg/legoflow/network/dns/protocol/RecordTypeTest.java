package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class RecordTypeTest {

    @Test
    void testStandardTypes() {
        assertThat(RecordType.A.value()).isEqualTo(1);
        assertThat(RecordType.NS.value()).isEqualTo(2);
        assertThat(RecordType.CNAME.value()).isEqualTo(5);
        assertThat(RecordType.SOA.value()).isEqualTo(6);
        assertThat(RecordType.PTR.value()).isEqualTo(12);
        assertThat(RecordType.MX.value()).isEqualTo(15);
        assertThat(RecordType.TXT.value()).isEqualTo(16);
        assertThat(RecordType.AAAA.value()).isEqualTo(28);
        assertThat(RecordType.SRV.value()).isEqualTo(33);
    }

    @Test
    void testExtendedTypes() {
        assertThat(RecordType.OPT.value()).isEqualTo(41);
        assertThat(RecordType.DS.value()).isEqualTo(43);
        assertThat(RecordType.RRSIG.value()).isEqualTo(46);
        assertThat(RecordType.NSEC.value()).isEqualTo(47);
        assertThat(RecordType.DNSKEY.value()).isEqualTo(48);
        assertThat(RecordType.NSEC3.value()).isEqualTo(50);
        assertThat(RecordType.NSEC3PARAM.value()).isEqualTo(51);
        assertThat(RecordType.CAA.value()).isEqualTo(257);
    }

    @ParameterizedTest
    @EnumSource(RecordType.class)
    void testFromValueRoundTrip(RecordType type) {
        assertThat(RecordType.fromValue(type.value())).isEqualTo(type);
    }

    @Test
    void testFromValueUnknown() {
        assertThatThrownBy(() -> RecordType.fromValue(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFromValueOrNull() {
        assertThat(RecordType.fromValueOrNull(1)).isEqualTo(RecordType.A);
        assertThat(RecordType.fromValueOrNull(999)).isNull();
    }
}
