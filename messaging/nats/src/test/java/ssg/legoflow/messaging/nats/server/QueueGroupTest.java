package ssg.legoflow.messaging.nats.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link QueueGroup}.
 */
class QueueGroupTest {

    @Test
    void testCreateGroup() {
        var group = new QueueGroup("workers");
        assertThat(group.name()).isEqualTo("workers");
        assertThat(group.isEmpty()).isTrue();
        assertThat(group.size()).isEqualTo(0);
    }

    @Test
    void testAddAndRemoveMember() {
        var group = new QueueGroup("g");
        var entry = new SubscriptionEntry(null, "1", "tasks", "g");

        group.addMember(entry);
        assertThat(group.size()).isEqualTo(1);
        assertThat(group.isEmpty()).isFalse();

        assertThat(group.removeMember(entry)).isTrue();
        assertThat(group.size()).isEqualTo(0);
    }

    @Test
    void testRoundRobin() {
        var group = new QueueGroup("g");
        var e1 = new SubscriptionEntry(null, "1", "t", "g");
        var e2 = new SubscriptionEntry(null, "2", "t", "g");
        var e3 = new SubscriptionEntry(null, "3", "t", "g");
        group.addMember(e1);
        group.addMember(e2);
        group.addMember(e3);

        // Round-robin should cycle through members
        var first = group.nextMember();
        var second = group.nextMember();
        var third = group.nextMember();
        var fourth = group.nextMember();

        assertThat(first).isEqualTo(e1);
        assertThat(second).isEqualTo(e2);
        assertThat(third).isEqualTo(e3);
        assertThat(fourth).isEqualTo(e1); // wraps around
    }

    @Test
    void testNextMemberEmptyGroup() {
        var group = new QueueGroup("g");
        assertThat(group.nextMember()).isNull();
    }

    @Test
    void testMembers() {
        var group = new QueueGroup("g");
        var e1 = new SubscriptionEntry(null, "1", "t", "g");
        var e2 = new SubscriptionEntry(null, "2", "t", "g");
        group.addMember(e1);
        group.addMember(e2);

        assertThat(group.members()).containsExactly(e1, e2);
    }

    @Test
    void testRemoveNonExistentMember() {
        var group = new QueueGroup("g");
        var entry = new SubscriptionEntry(null, "1", "t", "g");
        assertThat(group.removeMember(entry)).isFalse();
    }
}
