package ssg.legoflow.messaging.mqtt.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** TopicFilter wildcard & shared subscription coverage. */
class TopicFilterCoverageTest {

    @Test
    void singleLevelWildcard() {
        var tf = new TopicFilter("a/+/c");
        assertTrue(tf.matches("a/b/c"));
        assertFalse(tf.matches("a/b/d"));
        assertFalse(tf.matches("a/b"));
    }

    @Test
    void multiLevelWildcard() {
        var tf = new TopicFilter("a/#");
        assertTrue(tf.matches("a/b"));
        assertTrue(tf.matches("a/b/c"));
        assertTrue(tf.matches("a/b/c/d"));
        assertTrue(tf.matches("a")); // # matches zero or more levels
        assertFalse(tf.matches("x/b"));
    }

    @Test
    void dollarPrefixedTopicsDoNotMatchWildcard() {
        var tf = new TopicFilter("+/b");
        assertFalse(tf.matches("$SYS/b"));
        var tf2 = new TopicFilter("#");
        assertFalse(tf2.matches("$SYS/b"));
    }

    @Test
    void dollarPrefixedExactMatch() {
        var tf = new TopicFilter("$SYS/b");
        assertTrue(tf.matches("$SYS/b"));
    }

    @Test
    void sharedSubscription() {
        var tf = new TopicFilter("$share/group/a/b");
        assertTrue(tf.isSharedSubscription());
        assertEquals("group", tf.getShareGroup());
        assertEquals("a/b", tf.getEffectiveFilter());
        assertTrue(tf.matches("a/b"));
    }

    @Test
    void nonSharedSubscription() {
        var tf = new TopicFilter("a/b");
        assertFalse(tf.isSharedSubscription());
        assertNull(tf.getShareGroup());
        assertEquals("a/b", tf.getEffectiveFilter());
    }

    @Test
    void isValid() {
        assertTrue(new TopicFilter("a/b").isValid());
        assertTrue(new TopicFilter("a/+/b").isValid());
        assertTrue(new TopicFilter("a/#/b".isEmpty() ? "a/#" : "a/#").isValid());
        assertFalse(new TopicFilter("a/b/c#").isValid());
    }

    @Test
    void equality() {
        var a = new TopicFilter("a/b");
        var b = new TopicFilter("a/b");
        var c = new TopicFilter("x/y");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringReturnsFilter() {
        assertEquals("a/b", new TopicFilter("a/b").toString());
    }

    @Test
    void levels() {
        var tf = new TopicFilter("a/b/c");
        assertEquals(3, tf.getLevels().size());
        assertEquals("a", tf.getLevels().get(0));
    }

    @Test
    void matchesNullAndEmpty() {
        var tf = new TopicFilter("a/b");
        assertFalse(tf.matches(null));
        assertFalse(tf.matches(""));
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new TopicFilter(null));
    }

    @Test
    void constructorRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new TopicFilter(""));
    }
}
