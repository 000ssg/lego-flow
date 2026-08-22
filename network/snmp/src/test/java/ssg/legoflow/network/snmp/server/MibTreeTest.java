package ssg.legoflow.network.snmp.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.SnmpValue;
import java.util.NavigableMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link MibTree} in-memory MIB operations.
 *
 * @since 0.1.0
 */
class MibTreeTest {

    private MibTree tree;

    @BeforeEach
    void setUp() {
        tree = new MibTree();
        tree.put("1.3.6.1.2.1.1.1.0", SnmpValue.OctetString.of("Test System"));
        tree.put("1.3.6.1.2.1.1.2.0", SnmpValue.Oid.of("1.3.6.1.4.1.99"));
        tree.put("1.3.6.1.2.1.1.3.0", new SnmpValue.TimeTicks(12345));
        tree.put("1.3.6.1.2.1.1.4.0", SnmpValue.OctetString.of("admin@test"));
        tree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("test-host"));
        tree.put("1.3.6.1.2.1.1.6.0", SnmpValue.OctetString.of("Lab"));
        tree.put("1.3.6.1.2.1.1.7.0", new SnmpValue.Integer32(72));
    }

    @Test
    void testGetExistingOid() {
        SnmpValue value = tree.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        assertThat(value).isInstanceOf(SnmpValue.OctetString.class);
        assertThat(((SnmpValue.OctetString) value).asString()).isEqualTo("Test System");
    }

    @Test
    void testGetNonExistingOid() {
        assertThat(tree.get(ObjectIdentifier.parse("1.3.6.1.2.1.99.0"))).isNull();
    }

    @Test
    void testGetNextReturnsNextEntry() {
        var entry = tree.getNext(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        assertThat(entry).isNotNull();
        assertThat(entry.getKey().toDottedString()).isEqualTo("1.3.6.1.2.1.1.2.0");
    }

    @Test
    void testGetNextAtEndReturnsNull() {
        var entry = tree.getNext(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"));
        assertThat(entry).isNull();
    }

    @Test
    void testGetNextFromBeforeFirstReturnsFirst() {
        var entry = tree.getNext(ObjectIdentifier.parse("1.3.6.1.2.1.1.0"));
        assertThat(entry).isNotNull();
        assertThat(entry.getKey().toDottedString()).isEqualTo("1.3.6.1.2.1.1.1.0");
    }

    @Test
    void testGetCeiling() {
        var entry = tree.getCeiling(ObjectIdentifier.parse("1.3.6.1.2.1.1.3.0"));
        assertThat(entry).isNotNull();
        assertThat(entry.getKey().toDottedString()).isEqualTo("1.3.6.1.2.1.1.3.0");
    }

    @Test
    void testContains() {
        assertThat(tree.contains(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"))).isTrue();
        assertThat(tree.contains(ObjectIdentifier.parse("1.3.6.1.2.1.99.0"))).isFalse();
    }

    @Test
    void testRemove() {
        tree.remove(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"));
        assertThat(tree.contains(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"))).isFalse();
        assertThat(tree.size()).isEqualTo(6);
    }

    @Test
    void testGetSubtree() {
        NavigableMap<ObjectIdentifier, SnmpValue> subtree =
                tree.getSubtree(ObjectIdentifier.parse("1.3.6.1.2.1.1"));
        assertThat(subtree).hasSize(7);
    }

    @Test
    void testGetSubtreeNoMatch() {
        NavigableMap<ObjectIdentifier, SnmpValue> subtree =
                tree.getSubtree(ObjectIdentifier.parse("1.3.6.1.4.1"));
        assertThat(subtree).isEmpty();
    }

    @Test
    void testPutOverwritesExisting() {
        tree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("new-host"));
        SnmpValue value = tree.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"));
        assertThat(((SnmpValue.OctetString) value).asString()).isEqualTo("new-host");
    }

    @Test
    void testSize() {
        assertThat(tree.size()).isEqualTo(7);
    }

    @Test
    void testClear() {
        tree.clear();
        assertThat(tree.isEmpty()).isTrue();
        assertThat(tree.size()).isEqualTo(0);
    }

    @Test
    void testEntries() {
        NavigableMap<ObjectIdentifier, SnmpValue> all = tree.entries();
        assertThat(all).hasSize(7);
    }

    @Test
    void testPutRejectsNullOid() {
        assertThatThrownBy(() -> tree.put((ObjectIdentifier) null, SnmpValue.Null.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPutRejectsNullValue() {
        assertThatThrownBy(() -> tree.put("1.3.6.1.2.1.1.1.0", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
