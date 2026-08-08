package ssg.legoflow.network.snmp.protocol;

import ssg.legoflow.network.common.oid.ObjectIdentifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An ordered list of variable bindings (VarBind).
 *
 * <p>Immutable container used in SNMP PDUs to hold the list of
 * OID-value pairs being requested or returned.
 *
 * @param bindings the list of variable bindings
 * @since 0.1.0
 */
public record VarBindList(List<VarBind> bindings) implements Iterable<VarBind> {

    /**
     * Creates a VarBindList with defensive copy.
     *
     * @param bindings the list of variable bindings (must not be null)
     */
    public VarBindList {
        if (bindings == null) {
            throw new IllegalArgumentException("Bindings must not be null");
        }
        bindings = List.copyOf(bindings);
    }

    /**
     * Returns an empty VarBindList.
     *
     * @return the empty list
     */
    public static VarBindList empty() {
        return new VarBindList(List.of());
    }

    /**
     * Creates a VarBindList from the given bindings.
     *
     * @param bindings the variable bindings
     * @return the VarBindList
     */
    public static VarBindList of(VarBind... bindings) {
        return new VarBindList(List.of(bindings));
    }

    /**
     * Creates a VarBindList from a list of bindings.
     *
     * @param bindings the variable bindings
     * @return the VarBindList
     */
    public static VarBindList of(List<VarBind> bindings) {
        return new VarBindList(bindings);
    }

    /**
     * Returns the number of variable bindings.
     *
     * @return the size
     */
    public int size() {
        return bindings.size();
    }

    /**
     * Returns whether this list is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    /**
     * Returns the variable binding at the given index.
     *
     * @param index the index
     * @return the VarBind
     */
    public VarBind get(int index) {
        return bindings.get(index);
    }

    @Override
    public Iterator<VarBind> iterator() {
        return bindings.iterator();
    }

    /**
     * Returns a new builder for constructing a VarBindList.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing VarBindList incrementally.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private final List<VarBind> bindings = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a variable binding.
         *
         * @param varBind the binding to add
         * @return this builder
         */
        public Builder add(VarBind varBind) {
            bindings.add(varBind);
            return this;
        }

        /**
         * Adds a variable binding with null value (for GET requests).
         *
         * @param oid the object identifier
         * @return this builder
         */
        public Builder addNull(ObjectIdentifier oid) {
            bindings.add(VarBind.ofNull(oid));
            return this;
        }

        /**
         * Adds a variable binding with null value from dotted OID string.
         *
         * @param oid the dotted OID string
         * @return this builder
         */
        public Builder addNull(String oid) {
            bindings.add(VarBind.ofNull(oid));
            return this;
        }

        /**
         * Builds the VarBindList.
         *
         * @return the constructed VarBindList
         */
        public VarBindList build() {
            return new VarBindList(bindings);
        }
    }
}
