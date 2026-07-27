package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EnhancedStatusCode}.
 */
class EnhancedStatusCodeTest {

    @Test
    void testConstructValid() {
        var code = new EnhancedStatusCode(2, 1, 0);
        assertThat(code.statusClass()).isEqualTo(2);
        assertThat(code.subject()).isEqualTo(1);
        assertThat(code.detail()).isEqualTo(0);
    }

    @Test
    void testConstructAllClasses() {
        assertThatCode(() -> new EnhancedStatusCode(2, 0, 0)).doesNotThrowAnyException();
        assertThatCode(() -> new EnhancedStatusCode(4, 0, 0)).doesNotThrowAnyException();
        assertThatCode(() -> new EnhancedStatusCode(5, 0, 0)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 6, 9})
    void testConstructInvalidClass(int cls) {
        assertThatThrownBy(() -> new EnhancedStatusCode(cls, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructInvalidSubject() {
        assertThatThrownBy(() -> new EnhancedStatusCode(2, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnhancedStatusCode(2, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructInvalidDetail() {
        assertThatThrownBy(() -> new EnhancedStatusCode(2, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnhancedStatusCode(2, 0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2.1.0, 2, 1, 0",
            "5.7.1, 5, 7, 1",
            "4.3.1, 4, 3, 1",
            "2.0.0, 2, 0, 0",
            "5.5.4, 5, 5, 4",
            "4.2.1, 4, 2, 1"
    })
    void testParse(String text, int cls, int subject, int detail) {
        var code = EnhancedStatusCode.parse(text);
        assertThat(code.statusClass()).isEqualTo(cls);
        assertThat(code.subject()).isEqualTo(subject);
        assertThat(code.detail()).isEqualTo(detail);
    }

    @Test
    void testParseWithWhitespace() {
        var code = EnhancedStatusCode.parse("  2.1.0  ");
        assertThat(code.statusClass()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1.2.3", "2.x.0", "2.1", ""})
    void testParseInvalid(String text) {
        assertThatThrownBy(() -> EnhancedStatusCode.parse(text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseNull() {
        assertThatThrownBy(() -> EnhancedStatusCode.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIsSuccess() {
        assertThat(new EnhancedStatusCode(2, 0, 0).isSuccess()).isTrue();
        assertThat(new EnhancedStatusCode(4, 0, 0).isSuccess()).isFalse();
        assertThat(new EnhancedStatusCode(5, 0, 0).isSuccess()).isFalse();
    }

    @Test
    void testIsTransientFailure() {
        assertThat(new EnhancedStatusCode(2, 0, 0).isTransientFailure()).isFalse();
        assertThat(new EnhancedStatusCode(4, 0, 0).isTransientFailure()).isTrue();
        assertThat(new EnhancedStatusCode(5, 0, 0).isTransientFailure()).isFalse();
    }

    @Test
    void testIsPermanentFailure() {
        assertThat(new EnhancedStatusCode(2, 0, 0).isPermanentFailure()).isFalse();
        assertThat(new EnhancedStatusCode(4, 0, 0).isPermanentFailure()).isFalse();
        assertThat(new EnhancedStatusCode(5, 0, 0).isPermanentFailure()).isTrue();
    }

    @Test
    void testWireForm() {
        assertThat(new EnhancedStatusCode(2, 1, 0).wireForm()).isEqualTo("2.1.0");
        assertThat(new EnhancedStatusCode(5, 7, 1).wireForm()).isEqualTo("5.7.1");
    }

    @Test
    void testToString() {
        assertThat(new EnhancedStatusCode(2, 1, 0).toString()).isEqualTo("2.1.0");
    }

    @Test
    void testPredefinedConstants() {
        assertThat(EnhancedStatusCode.SUCCESS_OTHER.wireForm()).isEqualTo("2.0.0");
        assertThat(EnhancedStatusCode.SUCCESS_ADDRESS.wireForm()).isEqualTo("2.1.0");
        assertThat(EnhancedStatusCode.PERM_BAD_DEST_MAILBOX.wireForm()).isEqualTo("5.1.1");
        assertThat(EnhancedStatusCode.PERM_REFUSED.wireForm()).isEqualTo("5.7.1");
        assertThat(EnhancedStatusCode.TRANS_MAILBOX_BUSY.wireForm()).isEqualTo("4.2.1");
    }

    @Test
    void testEquality() {
        var a = new EnhancedStatusCode(2, 1, 0);
        var b = new EnhancedStatusCode(2, 1, 0);
        var c = new EnhancedStatusCode(5, 1, 0);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testRoundTrip() {
        var original = new EnhancedStatusCode(4, 3, 1);
        var parsed = EnhancedStatusCode.parse(original.wireForm());
        assertThat(parsed).isEqualTo(original);
    }
}
