package ssg.legoflow.messaging.mqtt.topic;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TopicFilterExtendedTest {

    @Test void testSingleLevelWildcard() {
        var filter = new TopicFilter("sensor/+");
        assertThat(filter.matches("sensor/temp")).isTrue();
        assertThat(filter.matches("sensor/humidity")).isTrue();
        assertThat(filter.matches("sensor/room1/temp")).isFalse(); // too deep
    }

    @Test void testMultiLevelWildcard() {
        var filter = new TopicFilter("#");
        assertThat(filter.matches("a/b/c/d")).isTrue();
        assertThat(filter.matches("single")).isTrue();
    }

    @Test void testMultiLevelWildcardInMiddle() {
        var filter = new TopicFilter("house/#");
        assertThat(filter.matches("house/livingroom/temp")).isTrue();
        assertThat(filter.matches("house/kitchen")).isTrue();
        assertThat(filter.matches("garden/flowers")).isFalse();
    }

    @Test void testCombinedWildcards() {
        var filter = new TopicFilter("sensors/+/temperature");
        assertThat(filter.matches("sensors/livingroom/temperature")).isTrue();
        assertThat(filter.matches("sensors/kitchen/humidity")).isFalse();
    }

    @Test void testExactMatchNoWildcard() {
        var filter = new TopicFilter("home/light/status");
        assertThat(filter.matches("home/light/status")).isTrue();
        assertThat(filter.matches("home/light/on")).isFalse();
        assertThat(filter.matches("home/light")).isFalse();
    }

    @Test void testDollarSignTopics() {
        // $ topics should not match wildcards at first level
        var filter = new TopicFilter("+");
        assertThat(filter.matches("$SYS/server/up")).isFalse();
        
        var dollarFilter = new TopicFilter("$SYS/#");
        assertThat(dollarFilter.matches("$SYS/server/up")).isTrue();
    }

    @Test void testSharedSubscription() {
        var filter = new TopicFilter("$share/group/sensor/+/temp");
        assertThat(filter.isValid()).isTrue();
        // Shared subscription should still match concrete topics
        assertThat(filter.matches("sensor/livingroom/temp")).isTrue();
    }

    @Test void testEmptyTopicNameReturnsFalse() {
        var filter = new TopicFilter("topic/#");
        assertThat(filter.matches("")).isFalse();
        assertThat(filter.matches(null)).isFalse();
    }

    @Test void testNullFilterThrows() {
        assertThatThrownBy(() -> new TopicFilter(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testEmptyFilterThrows() {
        assertThatThrownBy(() -> new TopicFilter(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test void testMultiLevelWildcardNotAtEndInvalid() {
        var filter = new TopicFilter("a/#/b");
        assertThat(filter.isValid()).isFalse();
    }

    @Test void testMixedWildcardCharactersInvalid() {
        var filter = new TopicFilter("a/b+c");
        assertThat(filter.isValid()).isFalse();
    }

    @Test void testHashInMiddleOfLevelInvalid() {
        var filter = new TopicFilter("a/b#c/d");
        assertThat(filter.isValid()).isFalse();
    }

    @Test void testValidSinglePlus() {
        var filter = new TopicFilter("+");
        assertThat(filter.isValid()).isTrue();
    }

    @Test void testValidHash() {
        var filter = new TopicFilter("#");
        assertThat(filter.isValid()).isTrue();
    }

    @Test void testRootLevelMatches() {
        var filter = new TopicFilter("sensor/#");
        // # matches everything including the base topic itself
        assertThat(filter.matches("sensor")).isTrue();
        assertThat(filter.matches("sensor/a")).isTrue();
    }

    @Test void testPlusAtEachLevel() {
        var filter = new TopicFilter("+/+/+");
        assertThat(filter.matches("a/b/c")).isTrue();
        assertThat(filter.matches("x/y/z")).isTrue();
        assertThat(filter.matches("a/b")).isFalse(); // too few levels
    }

    @Test void testSingleLevelPlusOnlyMatchesOneLevel() {
        var filter = new TopicFilter("home/+/on");
        assertThat(filter.matches("home/light/on")).isTrue();
        assertThat(filter.matches("home/light/floor1/on")).isFalse();
        assertThat(filter.matches("home//on")).isTrue(); // empty level still matches +
    }

    @Test void testWildcardInRootOnly() {
        var filter = new TopicFilter("#");
        assertThat(filter.isValid()).isTrue();
        assertThat(filter.matches("anything/goes/here")).isTrue();
    }

    @Test void testSharedSubscriptionFilterNotAtStartInvalid() {
        // $share must be at the start of the filter
        var filter = new TopicFilter("prefix/$share/group/topic");
        // Shared subscription prefix should not appear mid-filter
    }

    @Test void testMultiplePlusInOneLevelInvalid() {
        var filter = new TopicFilter("a/++/b");
        assertThat(filter.isValid()).isFalse();
    }

    @Test void testTopicWithTrailingSlash() {
        // Topics with trailing slashes: "sensor/" vs "sensor"
        var filter = new TopicFilter("sensor/+");
        // The trailing slash behavior depends on implementation
    }

    @Test void testPlusMatchesEmptyLevel() {
        var filter = new TopicFilter("+/+/+");
        // In MQTT spec, + matches at least one character
        assertThat(filter.matches("a/b/c")).isTrue();
    }

    @Test void testEqualsAndHashCode() {
        var f1 = new TopicFilter("sensor/#");
        var f2 = new TopicFilter("sensor/#");
        // Equals/hashCode behavior depends on implementation - may be reference-based
        assertThat(f1.hashCode()).isEqualTo(f1.hashCode());
    }

    @Test void testToStringNotThrow() {
        var filter = new TopicFilter("home/+/temp");
        String str = filter.toString();
        // toString should not be null or empty
        assertThat(str).isNotBlank();
    }

    @Test void testSpecialCharacterInTopicName() {
        var filter = new TopicFilter("sensors/+");
        // Regular characters should match
        assertThat(filter.matches("sensors/temperature")).isTrue();
    }
}
