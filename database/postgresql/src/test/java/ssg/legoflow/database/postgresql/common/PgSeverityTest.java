package ssg.legoflow.database.postgresql.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link PgSeverity}.
 */
class PgSeverityTest {

    @Test
    void testLabel() {
        assertThat(PgSeverity.ERROR.label()).isEqualTo("ERROR");
        assertThat(PgSeverity.FATAL.label()).isEqualTo("FATAL");
        assertThat(PgSeverity.WARNING.label()).isEqualTo("WARNING");
        assertThat(PgSeverity.NOTICE.label()).isEqualTo("NOTICE");
    }

    @Test
    void testFromLabelExact() {
        assertThat(PgSeverity.fromLabel("ERROR")).isEqualTo(PgSeverity.ERROR);
        assertThat(PgSeverity.fromLabel("FATAL")).isEqualTo(PgSeverity.FATAL);
        assertThat(PgSeverity.fromLabel("PANIC")).isEqualTo(PgSeverity.PANIC);
    }

    @Test
    void testFromLabelCaseInsensitive() {
        assertThat(PgSeverity.fromLabel("error")).isEqualTo(PgSeverity.ERROR);
        assertThat(PgSeverity.fromLabel("Warning")).isEqualTo(PgSeverity.WARNING);
    }

    @Test
    void testFromLabelUnknown() {
        assertThat(PgSeverity.fromLabel("UNKNOWN_LEVEL")).isEqualTo(PgSeverity.ERROR);
    }

    @Test
    void testAllValues() {
        assertThat(PgSeverity.values()).hasSize(8);
    }
}
