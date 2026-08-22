package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link Facility}.
 */
class FacilityTest {

    @Test
    void testAllFacilityCodes() {
        assertThat(Facility.KERN.code()).isEqualTo(0);
        assertThat(Facility.USER.code()).isEqualTo(1);
        assertThat(Facility.MAIL.code()).isEqualTo(2);
        assertThat(Facility.DAEMON.code()).isEqualTo(3);
        assertThat(Facility.AUTH.code()).isEqualTo(4);
        assertThat(Facility.SYSLOG.code()).isEqualTo(5);
        assertThat(Facility.LPR.code()).isEqualTo(6);
        assertThat(Facility.NEWS.code()).isEqualTo(7);
        assertThat(Facility.UUCP.code()).isEqualTo(8);
        assertThat(Facility.CRON.code()).isEqualTo(9);
        assertThat(Facility.AUTHPRIV.code()).isEqualTo(10);
        assertThat(Facility.FTP.code()).isEqualTo(11);
        assertThat(Facility.NTP.code()).isEqualTo(12);
        assertThat(Facility.AUDIT.code()).isEqualTo(13);
        assertThat(Facility.ALERT.code()).isEqualTo(14);
        assertThat(Facility.CLOCK.code()).isEqualTo(15);
        assertThat(Facility.LOCAL0.code()).isEqualTo(16);
        assertThat(Facility.LOCAL7.code()).isEqualTo(23);
    }

    @ParameterizedTest
    @EnumSource(Facility.class)
    void testRoundTripByCode(Facility facility) {
        assertThat(Facility.of(facility.code())).isEqualTo(facility);
    }

    @Test
    void testTotalCount() {
        assertThat(Facility.values()).hasSize(24);
    }

    @Test
    void testInvalidCode() {
        assertThatThrownBy(() -> Facility.of(24))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Facility.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
