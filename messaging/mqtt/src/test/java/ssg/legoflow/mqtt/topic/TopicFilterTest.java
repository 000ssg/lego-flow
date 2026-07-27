package ssg.legoflow.mqtt.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TopicFilter}.
 *
 * @since 1.0.0
 */
class TopicFilterTest {

    @Test
    void testExactMatch() {
        // Given: exact filter
        var filter = new TopicFilter("a/b/c");

        // When/Then: exact match works, non-match fails
        assertThat(filter.matches("a/b/c")).isTrue();
        assertThat(filter.matches("a/b")).isFalse();
        assertThat(filter.matches("a/b/c/d")).isFalse();
    }

    @Test
    void testSingleLevelWildcard() {
        // Given: filter with + wildcard
        var filter = new TopicFilter("a/+/c");

        // When/Then: matches any single level
        assertThat(filter.matches("a/b/c")).isTrue();
        assertThat(filter.matches("a/x/c")).isTrue();
        assertThat(filter.matches("a/b/d")).isFalse();
        assertThat(filter.matches("a/b/c/d")).isFalse();
    }

    @Test
    void testMultiLevelWildcard() {
        // Given: filter with # wildcard
        var filter = new TopicFilter("a/b/#");

        // When/Then: matches all remaining levels
        assertThat(filter.matches("a/b")).isTrue();
        assertThat(filter.matches("a/b/c")).isTrue();
        assertThat(filter.matches("a/b/c/d/e")).isTrue();
        assertThat(filter.matches("a/x")).isFalse();
    }

    @Test
    void testHashAlone() {
        // Given: # alone matches everything
        var filter = new TopicFilter("#");

        // When/Then: matches any topic
        assertThat(filter.matches("a")).isTrue();
        assertThat(filter.matches("a/b/c")).isTrue();
    }

    @Test
    void testPlusAlone() {
        // Given: + alone matches single level
        var filter = new TopicFilter("+");

        // When/Then: matches single level only
        assertThat(filter.matches("a")).isTrue();
        assertThat(filter.matches("a/b")).isFalse();
    }

    @Test
    void testSystemTopicNotMatchedByWildcard() {
        // Given: wildcards should not match $SYS topics
        var hash = new TopicFilter("#");
        var plus = new TopicFilter("+/info");

        // When/Then: $ topics not matched by leading wildcards
        assertThat(hash.matches("$SYS/info")).isFalse();
        assertThat(plus.matches("$SYS/info")).isFalse();
    }

    @Test
    void testSystemTopicMatchedByExactFilter() {
        // Given: explicit $ filter
        var filter = new TopicFilter("$SYS/#");

        // When/Then: matches $SYS topics
        assertThat(filter.matches("$SYS/info")).isTrue();
        assertThat(filter.matches("$SYS/a/b")).isTrue();
    }

    @Test
    void testIsValid() {
        // Given/When/Then: valid filters
        assertThat(new TopicFilter("a/b/c").isValid()).isTrue();
        assertThat(new TopicFilter("a/+/c").isValid()).isTrue();
        assertThat(new TopicFilter("a/#").isValid()).isTrue();
        assertThat(new TopicFilter("#").isValid()).isTrue();
    }

    @Test
    void testIsInvalid() {
        // Given/When/Then: invalid filters
        assertThat(new TopicFilter("a/#/b").isValid()).isFalse(); // # not last
        assertThat(new TopicFilter("a/b+c").isValid()).isFalse(); // + mixed with chars
    }

    @Test
    void testGetLevels() {
        // Given: multi-level filter
        var filter = new TopicFilter("a/b/c");

        // When/Then: levels split correctly
        assertThat(filter.getLevels()).containsExactly("a", "b", "c");
    }

    @Test
    void testSharedSubscription() {
        // Given: shared subscription
        var filter = new TopicFilter("$share/group1/sensors/+/data");

        // When/Then: correctly identified
        assertThat(filter.isSharedSubscription()).isTrue();
        assertThat(filter.getShareGroup()).isEqualTo("group1");
    }

    @Test
    void testNotSharedSubscription() {
        // Given: regular filter
        var filter = new TopicFilter("sensors/temperature");

        // When/Then: not a shared subscription
        assertThat(filter.isSharedSubscription()).isFalse();
        assertThat(filter.getShareGroup()).isNull();
    }
}
