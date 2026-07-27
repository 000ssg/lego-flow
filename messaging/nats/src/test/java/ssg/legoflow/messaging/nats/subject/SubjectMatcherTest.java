package ssg.legoflow.messaging.nats.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SubjectMatcher}.
 */
class SubjectMatcherTest {

    // --- Exact match ---

    @Test
    void testExactMatch() {
        assertThat(SubjectMatcher.matches("foo.bar", "foo.bar")).isTrue();
    }

    @Test
    void testExactMatchSingleToken() {
        assertThat(SubjectMatcher.matches("foo", "foo")).isTrue();
    }

    @Test
    void testExactMismatch() {
        assertThat(SubjectMatcher.matches("foo.bar", "foo.baz")).isFalse();
    }

    @Test
    void testExactMismatchDifferentLength() {
        assertThat(SubjectMatcher.matches("foo.bar", "foo.bar.baz")).isFalse();
        assertThat(SubjectMatcher.matches("foo.bar.baz", "foo.bar")).isFalse();
    }

    // --- Single wildcard * ---

    @Test
    void testSingleWildcard() {
        assertThat(SubjectMatcher.matches("foo.*", "foo.bar")).isTrue();
        assertThat(SubjectMatcher.matches("foo.*", "foo.baz")).isTrue();
    }

    @Test
    void testSingleWildcardMiddle() {
        assertThat(SubjectMatcher.matches("foo.*.baz", "foo.bar.baz")).isTrue();
        assertThat(SubjectMatcher.matches("foo.*.baz", "foo.xxx.baz")).isTrue();
    }

    @Test
    void testSingleWildcardDoesNotMatchMultiple() {
        assertThat(SubjectMatcher.matches("foo.*", "foo.bar.baz")).isFalse();
    }

    @Test
    void testSingleWildcardDoesNotMatchEmpty() {
        assertThat(SubjectMatcher.matches("foo.*", "foo")).isFalse();
    }

    @Test
    void testSingleWildcardAtStart() {
        assertThat(SubjectMatcher.matches("*.bar", "foo.bar")).isTrue();
        assertThat(SubjectMatcher.matches("*.bar", "baz.bar")).isTrue();
    }

    @Test
    void testMultipleSingleWildcards() {
        assertThat(SubjectMatcher.matches("*.*", "foo.bar")).isTrue();
        assertThat(SubjectMatcher.matches("*.*.*", "a.b.c")).isTrue();
        assertThat(SubjectMatcher.matches("*.*", "foo")).isFalse();
    }

    // --- Full wildcard > ---

    @Test
    void testFullWildcard() {
        assertThat(SubjectMatcher.matches("foo.>", "foo.bar")).isTrue();
        assertThat(SubjectMatcher.matches("foo.>", "foo.bar.baz")).isTrue();
        assertThat(SubjectMatcher.matches("foo.>", "foo.a.b.c.d")).isTrue();
    }

    @Test
    void testFullWildcardMatchesAtLeastOne() {
        assertThat(SubjectMatcher.matches("foo.>", "foo")).isFalse();
    }

    @Test
    void testFullWildcardAlone() {
        assertThat(SubjectMatcher.matches(">", "foo")).isTrue();
        assertThat(SubjectMatcher.matches(">", "foo.bar")).isTrue();
        assertThat(SubjectMatcher.matches(">", "a.b.c.d")).isTrue();
    }

    @Test
    void testFullWildcardWithPrefix() {
        assertThat(SubjectMatcher.matches("events.*.>", "events.user.login")).isTrue();
        assertThat(SubjectMatcher.matches("events.*.>", "events.system.restart.now")).isTrue();
    }

    // --- Subject object matching ---

    @Test
    void testMatchWithSubjectObjects() {
        assertThat(SubjectMatcher.matches(Subject.of("foo.*"), Subject.of("foo.bar"))).isTrue();
        assertThat(SubjectMatcher.matches(Subject.of("foo.>"), Subject.of("foo.bar.baz"))).isTrue();
        assertThat(SubjectMatcher.matches(Subject.of("foo.bar"), Subject.of("foo.bar"))).isTrue();
        assertThat(SubjectMatcher.matches(Subject.of("foo.bar"), Subject.of("foo.baz"))).isFalse();
    }

    // --- Edge cases ---

    @Test
    void testEmptyTokens() {
        assertThat(SubjectMatcher.matches("foo", "bar")).isFalse();
    }

    @Test
    void testWildcardDoesNotMatchPartialToken() {
        assertThat(SubjectMatcher.matches("foo.b*", "foo.bar")).isFalse();
    }

    @Test
    void testComplexPattern() {
        assertThat(SubjectMatcher.matches("a.*.c.>", "a.b.c.d")).isTrue();
        assertThat(SubjectMatcher.matches("a.*.c.>", "a.b.c.d.e")).isTrue();
        assertThat(SubjectMatcher.matches("a.*.c.>", "a.b.c")).isFalse();
        assertThat(SubjectMatcher.matches("a.*.c.>", "a.b.d.e")).isFalse();
    }
}
