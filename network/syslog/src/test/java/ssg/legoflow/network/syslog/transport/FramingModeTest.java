package ssg.legoflow.network.syslog.transport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link FramingMode}.
 */
class FramingModeTest {

    @Test
    void testValues() {
        assertThat(FramingMode.values()).hasSize(2);
        assertThat(FramingMode.OCTET_COUNTING).isNotNull();
        assertThat(FramingMode.NON_TRANSPARENT).isNotNull();
    }

    @Test
    void testValueOf() {
        assertThat(FramingMode.valueOf("OCTET_COUNTING")).isEqualTo(FramingMode.OCTET_COUNTING);
        assertThat(FramingMode.valueOf("NON_TRANSPARENT")).isEqualTo(FramingMode.NON_TRANSPARENT);
    }
}
