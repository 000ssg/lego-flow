package ssg.legoflow.database.postgresql.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SqlState}.
 */
class SqlStateTest {

    @Test
    void testSuccessfulCompletion() {
        assertThat(SqlState.SUCCESSFUL_COMPLETION.code()).isEqualTo("00000");
    }

    @Test
    void testUndefinedTable() {
        assertThat(SqlState.UNDEFINED_TABLE.code()).isEqualTo("42P01");
    }

    @Test
    void testUniqueViolation() {
        assertThat(SqlState.UNIQUE_VIOLATION.code()).isEqualTo("23505");
    }

    @Test
    void testSyntaxError() {
        assertThat(SqlState.SYNTAX_ERROR.code()).isEqualTo("42601");
    }

    @Test
    void testInvalidPassword() {
        assertThat(SqlState.INVALID_PASSWORD.code()).isEqualTo("28P01");
    }

    @Test
    void testFromCodeKnown() {
        assertThat(SqlState.fromCode("42P01")).isEqualTo(SqlState.UNDEFINED_TABLE);
        assertThat(SqlState.fromCode("00000")).isEqualTo(SqlState.SUCCESSFUL_COMPLETION);
        assertThat(SqlState.fromCode("23505")).isEqualTo(SqlState.UNIQUE_VIOLATION);
    }

    @Test
    void testFromCodeUnknown() {
        assertThat(SqlState.fromCode("99999")).isEqualTo(SqlState.INTERNAL_ERROR);
    }

    @Test
    void testCodeLength() {
        for (SqlState state : SqlState.values()) {
            assertThat(state.code()).hasSize(5);
        }
    }

    @Test
    void testProtocolViolation() {
        assertThat(SqlState.PROTOCOL_VIOLATION.code()).isEqualTo("08P01");
    }

    @Test
    void testInternalError() {
        assertThat(SqlState.INTERNAL_ERROR.code()).isEqualTo("XX000");
    }
}
