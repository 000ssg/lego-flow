package ssg.legoflow.rpc.grpc.metadata;

import java.util.*;

/**
 * A key-value metadata map for gRPC headers and trailers.
 * Keys are case-insensitive. Multiple values per key are supported.
 */
public class Metadata {

    private final Map<String, List<String>> entries = new LinkedHashMap<>();

    public Metadata() {
    }

    /**
     * Puts a single value, replacing any existing values for this key.
     */
    public Metadata put(String key, String value) {
        String normalizedKey = key.toLowerCase();
        var list = new ArrayList<String>();
        list.add(value);
        entries.put(normalizedKey, list);
        return this;
    }

    /**
     * Adds a value to the key, preserving existing values.
     */
    public Metadata add(String key, String value) {
        entries.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Puts a typed value.
     */
    public <T> Metadata put(MetadataKey<T> key, T value) {
        return put(key.name(), key.serialize(value));
    }

    /**
     * Adds a typed value.
     */
    public <T> Metadata add(MetadataKey<T> key, T value) {
        return add(key.name(), key.serialize(value));
    }

    /**
     * Gets the first value for the key.
     */
    public String get(String key) {
        var values = entries.get(key.toLowerCase());
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    /**
     * Gets a typed value.
     */
    public <T> T get(MetadataKey<T> key) {
        String raw = get(key.name());
        return raw != null ? key.deserialize(raw) : null;
    }

    /**
     * Gets all values for the key.
     */
    public List<String> getAll(String key) {
        var values = entries.get(key.toLowerCase());
        return values != null ? Collections.unmodifiableList(values) : List.of();
    }

    /**
     * Returns whether the key has any values.
     */
    public boolean containsKey(String key) {
        return entries.containsKey(key.toLowerCase());
    }

    /**
     * Returns all keys.
     */
    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /**
     * Removes a key.
     */
    public Metadata remove(String key) {
        entries.remove(key.toLowerCase());
        return this;
    }

    /**
     * Merges another metadata into this one.
     */
    public Metadata merge(Metadata other) {
        if (other != null) {
            for (var key : other.keys()) {
                for (var value : other.getAll(key)) {
                    add(key, value);
                }
            }
        }
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "Metadata" + entries;
    }
}
