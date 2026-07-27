package ssg.legoflow.messaging.kafka.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KafkaErrorsTest {

    @Test
    void testNoneCode() {
        assertThat(KafkaErrors.NONE.code()).isZero();
    }

    @Test
    void testForCodeNone() {
        assertThat(KafkaErrors.forCode((short) 0)).isEqualTo(KafkaErrors.NONE);
    }

    @Test
    void testForCodeUnknown() {
        assertThat(KafkaErrors.forCode((short) 9999)).isEqualTo(KafkaErrors.UNKNOWN_SERVER_ERROR);
    }

    @Test
    void testForCodeNegative() {
        assertThat(KafkaErrors.forCode((short) -1)).isEqualTo(KafkaErrors.UNKNOWN_SERVER_ERROR);
    }

    @Test
    void testCommonErrorCodes() {
        assertThat(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code()).isEqualTo((short) 3);
        assertThat(KafkaErrors.LEADER_NOT_AVAILABLE.code()).isEqualTo((short) 5);
        assertThat(KafkaErrors.REBALANCE_IN_PROGRESS.code()).isEqualTo((short) 27);
        assertThat(KafkaErrors.TOPIC_ALREADY_EXISTS.code()).isEqualTo((short) 36);
    }

    @Test
    void testMessages() {
        assertThat(KafkaErrors.NONE.message()).isEqualTo("No error");
        assertThat(KafkaErrors.UNKNOWN_MEMBER_ID.message()).contains("member");
    }

    @Test
    void testAllCodesUnique() {
        var codes = new java.util.HashSet<Short>();
        for (KafkaErrors e : KafkaErrors.values()) {
            assertThat(codes.add(e.code())).as("Duplicate code: " + e.code()).isTrue();
        }
    }
}
