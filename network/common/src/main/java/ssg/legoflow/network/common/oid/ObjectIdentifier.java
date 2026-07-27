package ssg.legoflow.network.common.oid;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Immutable OID (Object Identifier) value object.
 *
 * <p>An OID is a sequence of non-negative integer arcs that uniquely identifies
 * an object in the ASN.1 registration tree. Examples: {@code 1.3.6.1.2.1.1}
 * (iso.org.dod.internet.mgmt.mib-2.system).
 *
 * <p>The first arc must be 0, 1, or 2. If the first arc is 0 or 1, the second
 * arc must be less than 40. An OID must have at least 2 arcs.
 *
 * @since 1.0.0
 */
public final class ObjectIdentifier implements Comparable<ObjectIdentifier> {

    private final int[] arcs;
    private final String dotted;

    private ObjectIdentifier(int[] arcs) {
        this.arcs = arcs;
        this.dotted = IntStream.of(arcs)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining("."));
    }

    /**
     * Parses a dotted string OID representation.
     *
     * @param dotted the dotted string (e.g. "1.3.6.1.2.1.1")
     * @return the parsed OID
     * @throws IllegalArgumentException if the string is invalid
     */
    public static ObjectIdentifier parse(String dotted) {
        if (dotted == null || dotted.isEmpty()) {
            throw new IllegalArgumentException("OID string must not be null or empty");
        }
        String[] parts = dotted.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("OID must have at least 2 arcs: " + dotted);
        }
        int[] arcs = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                arcs[i] = Integer.parseUnsignedInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid arc value: " + parts[i], e);
            }
        }
        validate(arcs);
        return new ObjectIdentifier(arcs);
    }

    /**
     * Creates an OID from integer arcs.
     *
     * @param arcs the arc values (at least 2)
     * @return the OID
     * @throws IllegalArgumentException if the arcs are invalid
     */
    public static ObjectIdentifier of(int... arcs) {
        if (arcs == null || arcs.length < 2) {
            throw new IllegalArgumentException("OID must have at least 2 arcs");
        }
        int[] copy = arcs.clone();
        validate(copy);
        return new ObjectIdentifier(copy);
    }

    private static void validate(int[] arcs) {
        if (arcs[0] < 0 || arcs[0] > 2) {
            throw new IllegalArgumentException("First arc must be 0, 1, or 2: " + arcs[0]);
        }
        if ((arcs[0] == 0 || arcs[0] == 1) && arcs[1] >= 40) {
            throw new IllegalArgumentException(
                    "Second arc must be < 40 when first arc is " + arcs[0] + ": " + arcs[1]);
        }
        for (int arc : arcs) {
            if (arc < 0) {
                throw new IllegalArgumentException("Arc values must be non-negative: " + arc);
            }
        }
    }

    /**
     * Returns the arc values.
     *
     * @return a copy of the arc array
     */
    public int[] arcs() {
        return arcs.clone();
    }

    /**
     * Returns the number of arcs.
     *
     * @return the arc count
     */
    public int size() {
        return arcs.length;
    }

    /**
     * Returns the arc at the given index.
     *
     * @param index the zero-based index
     * @return the arc value
     */
    public int arc(int index) {
        return arcs[index];
    }

    /**
     * Returns the dotted string representation.
     *
     * @return the dotted OID string
     */
    public String toDottedString() {
        return dotted;
    }

    /**
     * Returns whether this OID starts with the given prefix.
     *
     * @param prefix the prefix OID
     * @return true if this OID starts with the prefix
     */
    public boolean startsWith(ObjectIdentifier prefix) {
        if (prefix.arcs.length > arcs.length) {
            return false;
        }
        for (int i = 0; i < prefix.arcs.length; i++) {
            if (arcs[i] != prefix.arcs[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a child OID by appending the given arc.
     *
     * @param arc the arc to append
     * @return the child OID
     */
    public ObjectIdentifier child(int arc) {
        int[] newArcs = Arrays.copyOf(arcs, arcs.length + 1);
        newArcs[arcs.length] = arc;
        return new ObjectIdentifier(newArcs);
    }

    @Override
    public int compareTo(ObjectIdentifier other) {
        int len = Math.min(arcs.length, other.arcs.length);
        for (int i = 0; i < len; i++) {
            int cmp = Integer.compareUnsigned(arcs[i], other.arcs[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(arcs.length, other.arcs.length);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ObjectIdentifier other && Arrays.equals(arcs, other.arcs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(arcs);
    }

    @Override
    public String toString() {
        return dotted;
    }
}
