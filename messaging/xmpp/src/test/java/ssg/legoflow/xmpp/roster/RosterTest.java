package ssg.legoflow.xmpp.roster;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Roster}.
 *
 * @since 0.1.0
 */
class RosterTest {

    private Roster roster;
    private final JID alice = JID.parse("alice@example.com");
    private final JID bob = JID.parse("bob@example.com");

    @BeforeEach
    void setUp() {
        roster = new Roster();
    }

    @Test
    void testAddItem() {
        roster.addItem(alice, "Alice", "Friends");
        assertThat(roster.size()).isEqualTo(1);
        assertThat(roster.getItem(alice)).isNotNull();
        assertThat(roster.getItem(alice).name()).isEqualTo("Alice");
    }

    @Test
    void testRemoveItem() {
        roster.addItem(alice, "Alice");
        roster.removeItem(alice);
        assertThat(roster.size()).isEqualTo(0);
        assertThat(roster.getItem(alice)).isNull();
    }

    @Test
    void testUpdateItem() {
        roster.addItem(alice, "Alice");
        var updated = new RosterItem(alice, "Alice Updated",
                RosterItem.SubscriptionType.BOTH, List.of("Work"));
        roster.updateItem(updated);
        assertThat(roster.getItem(alice).name()).isEqualTo("Alice Updated");
        assertThat(roster.getItem(alice).subscription()).isEqualTo(RosterItem.SubscriptionType.BOTH);
    }

    @Test
    void testRosterPush() {
        roster.addItem(alice, "Alice");
        var pushed = new RosterItem(alice, "Alice New",
                RosterItem.SubscriptionType.TO, List.of("Friends"));
        roster.handleRosterPush(pushed);
        assertThat(roster.getItem(alice).name()).isEqualTo("Alice New");
    }

    @Test
    void testRosterPushRemove() {
        roster.addItem(alice, "Alice");
        var removal = new RosterItem(alice, "Alice",
                RosterItem.SubscriptionType.REMOVE, List.of());
        roster.handleRosterPush(removal);
        assertThat(roster.size()).isEqualTo(0);
    }

    @Test
    void testGroups() {
        roster.addItem(alice, "Alice", "Friends", "Work");
        roster.addItem(bob, "Bob", "Work");
        assertThat(roster.getGroups()).containsExactly("Friends", "Work");
        assertThat(roster.getItemsByGroup("Work")).hasSize(2);
        assertThat(roster.getItemsByGroup("Friends")).hasSize(1);
    }

    @Test
    void testListener() {
        var added = new ArrayList<RosterItem>();
        roster.addListener(new RosterListener() {
            @Override public void onRosterLoaded(List<RosterItem> items) {}
            @Override public void onItemAdded(RosterItem item) { added.add(item); }
            @Override public void onItemUpdated(RosterItem item) {}
            @Override public void onItemRemoved(JID jid) {}
        });
        roster.addItem(alice, "Alice");
        assertThat(added).hasSize(1);
    }

    @Test
    void testLoad() {
        var items = List.of(
                new RosterItem(alice, "Alice", RosterItem.SubscriptionType.BOTH, List.of("Friends")),
                new RosterItem(bob, "Bob", RosterItem.SubscriptionType.TO, List.of("Work")));
        roster.load(items);
        assertThat(roster.isLoaded()).isTrue();
        assertThat(roster.size()).isEqualTo(2);
    }
}
