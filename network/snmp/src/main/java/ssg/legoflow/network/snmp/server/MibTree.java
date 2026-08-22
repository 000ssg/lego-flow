package ssg.legoflow.network.snmp.server;

import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.SnmpValue;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
/**
 * Thread-safe in-memory MIB tree for an SNMP agent.
 *
 * <p>Stores OID-value pairs in a sorted tree structure, supporting
 * exact lookups, lexicographic next-OID queries, and subtree operations.
 * Uses a {@link ConcurrentSkipListMap} for lock-free concurrent access.
 *
 * @since 0.1.0
 */
public final class MibTree {

    private final ConcurrentSkipListMap<ObjectIdentifier, SnmpValue> entries =
            new ConcurrentSkipListMap<>();

    /**
     * Sets a value for the given OID, creating or updating it.
     *
     * @param oid   the object identifier
     * @param value the SNMP value
     */
    public void put(ObjectIdentifier oid, SnmpValue value) {
        if (oid == null) throw new IllegalArgumentException("OID must not be null");
        if (value == null) throw new IllegalArgumentException("Value must not be null");
        entries.put(oid, value);
    }

    /**
     * Sets a value using dotted OID notation.
     *
     * @param oid   the dotted OID string
     * @param value the SNMP value
     */
    public void put(String oid, SnmpValue value) {
        put(ObjectIdentifier.parse(oid), value);
    }

    /**
     * Returns the value for the given OID.
     *
     * @param oid the object identifier
     * @return the value, or null if not found
     */
    public SnmpValue get(ObjectIdentifier oid) {
        return entries.get(oid);
    }

    /**
     * Returns the next OID entry after the given OID (lexicographic order).
     *
     * @param oid the starting OID (exclusive)
     * @return the next entry, or null if no more entries
     */
    public Map.Entry<ObjectIdentifier, SnmpValue> getNext(ObjectIdentifier oid) {
        return entries.higherEntry(oid);
    }

    /**
     * Returns the next OID entry at or after the given OID.
     *
     * @param oid the starting OID (inclusive)
     * @return the ceiling entry, or null if no match
     */
    public Map.Entry<ObjectIdentifier, SnmpValue> getCeiling(ObjectIdentifier oid) {
        return entries.ceilingEntry(oid);
    }

    /**
     * Removes the entry for the given OID.
     *
     * @param oid the object identifier
     * @return the removed value, or null if not found
     */
    public SnmpValue remove(ObjectIdentifier oid) {
        return entries.remove(oid);
    }

    /**
     * Returns whether the tree contains the given OID.
     *
     * @param oid the object identifier
     * @return true if the OID exists in the tree
     */
    public boolean contains(ObjectIdentifier oid) {
        return entries.containsKey(oid);
    }

    /**
     * Returns all entries under the given subtree (inclusive).
     *
     * @param subtreeRoot the subtree root OID
     * @return a navigable map of matching entries
     */
    public NavigableMap<ObjectIdentifier, SnmpValue> getSubtree(ObjectIdentifier subtreeRoot) {
        // Find the upper bound: the OID that is just past the subtree
        // We iterate and include entries that start with the subtree root
        ObjectIdentifier from = subtreeRoot;
        Map.Entry<ObjectIdentifier, SnmpValue> last = null;
        NavigableMap<ObjectIdentifier, SnmpValue> result = new ConcurrentSkipListMap<>();

        for (Map.Entry<ObjectIdentifier, SnmpValue> entry : entries.tailMap(from, true).entrySet()) {
            if (entry.getKey().startsWith(subtreeRoot)) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                break; // Past the subtree since the map is sorted
            }
        }
        return result;
    }

    /**
     * Returns the number of entries in the tree.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns whether the tree is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Removes all entries from the tree.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Returns an unmodifiable view of all entries.
     *
     * @return the navigable map of all entries
     */
    public NavigableMap<ObjectIdentifier, SnmpValue> entries() {
        return java.util.Collections.unmodifiableNavigableMap(entries);
    }
}
