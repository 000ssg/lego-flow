package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ImapTag}.
 *
 * @since 0.1.0
 */
class ImapTagTest {

    @Test
    void testDefaultPrefix() {
        ImapTag tag = new ImapTag();
        assertThat(tag.next()).isEqualTo("A001");
        assertThat(tag.next()).isEqualTo("A002");
        assertThat(tag.next()).isEqualTo("A003");
    }

    @Test
    void testCustomPrefix() {
        ImapTag tag = new ImapTag("T");
        assertThat(tag.next()).isEqualTo("T001");
        assertThat(tag.next()).isEqualTo("T002");
    }

    @Test
    void testReset() {
        ImapTag tag = new ImapTag();
        tag.next();
        tag.next();
        tag.reset();
        assertThat(tag.next()).isEqualTo("A001");
    }

    @Test
    void testCurrent() {
        ImapTag tag = new ImapTag();
        assertThat(tag.current()).isEqualTo(0);
        tag.next();
        assertThat(tag.current()).isEqualTo(1);
    }

    @Test
    void testUntaggedConstant() {
        assertThat(ImapTag.UNTAGGED).isEqualTo("*");
    }

    @Test
    void testContinuationConstant() {
        assertThat(ImapTag.CONTINUATION).isEqualTo("+");
    }

    @Test
    void testSequentialTags() {
        ImapTag tag = new ImapTag("X");
        for (int i = 1; i <= 100; i++) {
            String expected = "X" + String.format("%03d", i);
            assertThat(tag.next()).isEqualTo(expected);
        }
    }
}
