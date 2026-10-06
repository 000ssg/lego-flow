package ssg.legoflow.messaging.mqtt.topic;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SharedSubscriptionRegistry}.
 *
 * @since 0.2.0
 */
class SharedSubscriptionRegistryTest {

    @Test
    void testRegisterAndSelectNext() {
        var reg = new SharedSubscriptionRegistry<String>();
        reg.register("group1", "sensors/+", "client-A");
        reg.register("group1", "sensors/#", "client-B");

        // With 2 members, round-robin alternates
        assertThat(reg.selectNext("group1")).isEqualTo("client-A");
        assertThat(reg.selectNext("group1")).isEqualTo("client-B");
        assertThat(reg.selectNext("group1")).isEqualTo("client-A");
        assertThat(reg.selectNext("group1")).isEqualTo("client-B");
    }

    @Test
    void testUnregisterRemovesSubscriber() {
        var reg = new SharedSubscriptionRegistry<String>();
        reg.register("g", "sensors/+", "A");
        reg.register("g", "sensors/#", "B");

        reg.unregister("g", "sensors/+", "A");
        // Only B remains — selectNext always returns B
        assertThat(reg.selectNext("g")).isEqualTo("B");
        assertThat(reg.selectNext("g")).isEqualTo("B");
    }

    @Test
    void testEmptyGroupReturnsNull() {
        var reg = new SharedSubscriptionRegistry<String>();
        assertThat(reg.selectNext("nonexistent")).isNull();
    }

    @Test
    void testGroupRemovedWhenEmpty() {
        var reg = new SharedSubscriptionRegistry<String>();
        reg.register("g", "t", "A");
        reg.unregister("g", "t", "A");
        assertThat(reg.getGroups()).isEmpty();
    }

    @Test
    void testMultipleGroupsIndependent() {
        var reg = new SharedSubscriptionRegistry<String>();
        reg.register("g1", "t1", "A");
        reg.register("g1", "t1", "B");
        reg.register("g2", "t2", "C");

        // g1 alternates A, B; g2 always returns C (only member)
        assertThat(reg.selectNext("g1")).isEqualTo("A");
        assertThat(reg.selectNext("g2")).isEqualTo("C");
        assertThat(reg.selectNext("g1")).isEqualTo("B");
        assertThat(reg.selectNext("g2")).isEqualTo("C");
    }

    @Test
    void testTotalSubscribers() {
        var reg = new SharedSubscriptionRegistry<String>();
        reg.register("g1", "t1", "A");
        reg.register("g1", "t1", "B");
        reg.register("g2", "t2", "C");
        assertThat(reg.totalSubscribers()).isEqualTo(3);
    }
}
