package ssg.legoflow.database.postgresql.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link TransactionStatus}.
 */
class TransactionStatusTest {

    @Test
    void testIdleIndicator() {
        assertThat(TransactionStatus.IDLE.indicator()).isEqualTo((byte) 'I');
    }

    @Test
    void testInTransactionIndicator() {
        assertThat(TransactionStatus.IN_TRANSACTION.indicator()).isEqualTo((byte) 'T');
    }

    @Test
    void testFailedIndicator() {
        assertThat(TransactionStatus.FAILED.indicator()).isEqualTo((byte) 'E');
    }

    @Test
    void testFromByteIdle() {
        assertThat(TransactionStatus.fromByte((byte) 'I')).isEqualTo(TransactionStatus.IDLE);
    }

    @Test
    void testFromByteInTransaction() {
        assertThat(TransactionStatus.fromByte((byte) 'T')).isEqualTo(TransactionStatus.IN_TRANSACTION);
    }

    @Test
    void testFromByteFailed() {
        assertThat(TransactionStatus.fromByte((byte) 'E')).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testFromByteInvalid() {
        assertThatThrownBy(() -> TransactionStatus.fromByte((byte) 'X'))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
