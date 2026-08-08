package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class ResponseCodeTest {

    @Test
    void testStandardCodes() {
        assertThat(ResponseCode.NOERROR.value()).isEqualTo(0);
        assertThat(ResponseCode.FORMERR.value()).isEqualTo(1);
        assertThat(ResponseCode.SERVFAIL.value()).isEqualTo(2);
        assertThat(ResponseCode.NXDOMAIN.value()).isEqualTo(3);
        assertThat(ResponseCode.NOTIMP.value()).isEqualTo(4);
        assertThat(ResponseCode.REFUSED.value()).isEqualTo(5);
    }

    @Test
    void testUpdateCodes() {
        assertThat(ResponseCode.YXDOMAIN.value()).isEqualTo(6);
        assertThat(ResponseCode.YXRRSET.value()).isEqualTo(7);
        assertThat(ResponseCode.NXRRSET.value()).isEqualTo(8);
    }

    @Test
    void testFromValue() {
        assertThat(ResponseCode.fromValue(0)).isEqualTo(ResponseCode.NOERROR);
        assertThat(ResponseCode.fromValue(3)).isEqualTo(ResponseCode.NXDOMAIN);
    }

    @Test
    void testRoundTripAllCodes() {
        for (ResponseCode rc : ResponseCode.values()) {
            assertThat(ResponseCode.fromValue(rc.value())).isEqualTo(rc);
        }
    }

    @Test
    void testFromValueUnknown() {
        assertThatThrownBy(() -> ResponseCode.fromValue(99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
