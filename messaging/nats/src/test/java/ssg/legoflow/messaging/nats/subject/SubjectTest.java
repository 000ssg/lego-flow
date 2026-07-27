package ssg.legoflow.messaging.nats.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Subject}.
 */
class SubjectTest {

    @Test
    void testSimpleSubject() {
        var s = Subject.of("foo.bar.baz");
        assertThat(s.value()).isEqualTo("foo.bar.baz");
        assertThat(s.tokenCount()).isEqualTo(3);
        assertThat(s.tokenAt(0)).isEqualTo("foo");
        assertThat(s.tokenAt(1)).isEqualTo("bar");
        assertThat(s.tokenAt(2)).isEqualTo("baz");
    }

    @Test
    void testSingleToken() {
        var s = Subject.of("events");
        assertThat(s.tokenCount()).isEqualTo(1);
        assertThat(s.tokenAt(0)).isEqualTo("events");
    }

    @Test
    void testHasWildcards() {
        assertThat(Subject.of("foo.bar").hasWildcards()).isFalse();
        assertThat(Subject.of("foo.*").hasWildcards()).isTrue();
        assertThat(Subject.of("foo.>").hasWildcards()).isTrue();
        assertThat(Subject.of("*.bar").hasWildcards()).isTrue();
    }

    @Test
    void testIsPublishable() {
        assertThat(Subject.of("foo.bar").isPublishable()).isTrue();
        assertThat(Subject.of("foo.*").isPublishable()).isFalse();
        assertThat(Subject.of("foo.>").isPublishable()).isFalse();
    }

    @Test
    void testNullSubjectThrows() {
        assertThatThrownBy(() -> Subject.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEmptySubjectThrows() {
        assertThatThrownBy(() -> Subject.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSpacesInSubjectThrows() {
        assertThatThrownBy(() -> Subject.of("foo bar"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEquality() {
        assertThat(Subject.of("foo.bar")).isEqualTo(Subject.of("foo.bar"));
        assertThat(Subject.of("foo.bar")).isNotEqualTo(Subject.of("foo.baz"));
    }

    @Test
    void testHashCode() {
        assertThat(Subject.of("foo.bar").hashCode())
                .isEqualTo(Subject.of("foo.bar").hashCode());
    }

    @Test
    void testToString() {
        assertThat(Subject.of("foo.bar").toString()).isEqualTo("foo.bar");
    }
}
