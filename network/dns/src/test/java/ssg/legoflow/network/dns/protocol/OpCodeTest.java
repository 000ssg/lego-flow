package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class OpCodeTest {

    @Test
    void testStandardOpCodes() {
        assertThat(OpCode.QUERY.value()).isEqualTo(0);
        assertThat(OpCode.IQUERY.value()).isEqualTo(1);
        assertThat(OpCode.STATUS.value()).isEqualTo(2);
        assertThat(OpCode.NOTIFY.value()).isEqualTo(4);
        assertThat(OpCode.UPDATE.value()).isEqualTo(5);
    }

    @Test
    void testFromValue() {
        assertThat(OpCode.fromValue(0)).isEqualTo(OpCode.QUERY);
        assertThat(OpCode.fromValue(5)).isEqualTo(OpCode.UPDATE);
    }

    @Test
    void testRoundTripAllOpCodes() {
        for (OpCode op : OpCode.values()) {
            assertThat(OpCode.fromValue(op.value())).isEqualTo(op);
        }
    }

    @Test
    void testFromValueUnknown() {
        assertThatThrownBy(() -> OpCode.fromValue(99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
