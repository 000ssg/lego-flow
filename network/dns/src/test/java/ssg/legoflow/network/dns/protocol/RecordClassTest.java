package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class RecordClassTest {

    @Test
    void testStandardClasses() {
        assertThat(RecordClass.IN.value()).isEqualTo(1);
        assertThat(RecordClass.CH.value()).isEqualTo(3);
        assertThat(RecordClass.HS.value()).isEqualTo(4);
        assertThat(RecordClass.ANY.value()).isEqualTo(255);
    }

    @Test
    void testFromValue() {
        assertThat(RecordClass.fromValue(1)).isEqualTo(RecordClass.IN);
        assertThat(RecordClass.fromValue(255)).isEqualTo(RecordClass.ANY);
    }

    @Test
    void testRoundTripAllClasses() {
        for (RecordClass rc : RecordClass.values()) {
            assertThat(RecordClass.fromValue(rc.value())).isEqualTo(rc);
        }
    }

    @Test
    void testFromValueUnknown() {
        assertThatThrownBy(() -> RecordClass.fromValue(99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
