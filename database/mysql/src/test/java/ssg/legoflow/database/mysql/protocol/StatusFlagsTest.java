package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StatusFlags}.
 */
class StatusFlagsTest {

    @Test
    void testHasStatus_autocommit() {
        assertThat(StatusFlags.hasStatus(StatusFlags.DEFAULT_STATUS,
                StatusFlags.SERVER_STATUS_AUTOCOMMIT)).isTrue();
    }

    @Test
    void testHasStatus_notInTransaction() {
        assertThat(StatusFlags.hasStatus(StatusFlags.DEFAULT_STATUS,
                StatusFlags.SERVER_STATUS_IN_TRANS)).isFalse();
    }

    @Test
    void testHasStatus_combined() {
        int flags = StatusFlags.SERVER_STATUS_IN_TRANS | StatusFlags.SERVER_STATUS_AUTOCOMMIT;
        assertThat(StatusFlags.hasStatus(flags, StatusFlags.SERVER_STATUS_IN_TRANS)).isTrue();
        assertThat(StatusFlags.hasStatus(flags, StatusFlags.SERVER_STATUS_AUTOCOMMIT)).isTrue();
    }

    @Test
    void testToString_default() {
        String str = StatusFlags.toString(StatusFlags.DEFAULT_STATUS);
        assertThat(str).contains("AUTOCOMMIT");
    }

    @Test
    void testToString_empty() {
        assertThat(StatusFlags.toString(0)).isEmpty();
    }

    @Test
    void testToString_multiple() {
        int flags = StatusFlags.SERVER_STATUS_IN_TRANS
                | StatusFlags.SERVER_STATUS_AUTOCOMMIT
                | StatusFlags.SERVER_MORE_RESULTS_EXISTS;
        String str = StatusFlags.toString(flags);
        assertThat(str).contains("IN_TRANS");
        assertThat(str).contains("AUTOCOMMIT");
        assertThat(str).contains("MORE_RESULTS_EXISTS");
    }
}
