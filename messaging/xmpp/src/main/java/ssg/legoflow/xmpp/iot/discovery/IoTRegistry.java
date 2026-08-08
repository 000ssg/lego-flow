package ssg.legoflow.xmpp.iot.discovery;

import ssg.legoflow.xmpp.core.JID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IoT thing registry for discovery (XEP-0347).
 *
 * <p>Manages registration, claiming, searching, and lifecycle of IoT things.
 *
 * @since 0.1.0
 */
public class IoTRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(IoTRegistry.class);

    private final Map<String, ThingDescription> things = new ConcurrentHashMap<>();

    /**
     * Creates a new empty registry.
     */
    public IoTRegistry() {
    }

    /**
     * Registers a thing in the registry.
     *
     * @param thing the thing to register
     */
    public void register(ThingDescription thing) {
        Objects.requireNonNull(thing, "thing must not be null");
        things.put(thing.nodeId(), thing);
        LOG.info("Registered thing: {} ({})", thing.nodeId(), thing.name());
    }

    /**
     * Unregisters a thing from the registry.
     *
     * @param nodeId the node identifier
     * @return the removed thing, or null if not found
     */
    public ThingDescription unregister(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        var removed = things.remove(nodeId);
        if (removed != null) {
            LOG.info("Unregistered thing: {}", nodeId);
        }
        return removed;
    }

    /**
     * Claims a thing for an owner.
     *
     * @param nodeId the node identifier
     * @param owner  the claiming owner's JID
     * @return true if the thing was claimed successfully
     */
    public boolean claim(String nodeId, JID owner) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        var thing = things.get(nodeId);
        if (thing == null) {
            LOG.warn("Cannot claim unknown thing: {}", nodeId);
            return false;
        }
        if (thing.claimed()) {
            LOG.warn("Thing already claimed: {}", nodeId);
            return false;
        }
        things.put(nodeId, thing.withOwner(owner));
        LOG.info("Thing {} claimed by {}", nodeId, owner.toBareJid());
        return true;
    }

    /**
     * Disowns a thing, removing its owner.
     *
     * @param nodeId the node identifier
     * @return true if the thing was disowned successfully
     */
    public boolean disown(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        var thing = things.get(nodeId);
        if (thing == null) {
            return false;
        }
        things.put(nodeId, thing.disown());
        LOG.info("Thing {} disowned", nodeId);
        return true;
    }

    /**
     * Searches for things matching the given tags.
     *
     * @param tags the search tags (all must match)
     * @return the matching things
     */
    public List<ThingDescription> search(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return getThings();
        }
        return things.values().stream()
                .filter(thing -> tags.entrySet().stream()
                        .allMatch(entry -> {
                            // Check tags
                            if (thing.tags().containsKey(entry.getKey())) {
                                return thing.tags().get(entry.getKey()).equals(entry.getValue());
                            }
                            // Check built-in fields
                            return switch (entry.getKey()) {
                                case "manufacturer" -> entry.getValue().equals(thing.manufacturer());
                                case "model" -> entry.getValue().equals(thing.model());
                                case "name" -> entry.getValue().equals(thing.name());
                                default -> false;
                            };
                        }))
                .toList();
    }

    /**
     * Returns all registered things.
     *
     * @return the list of all things
     */
    public List<ThingDescription> getThings() {
        return List.copyOf(things.values());
    }

    /**
     * Returns a thing by its node identifier.
     *
     * @param nodeId the node identifier
     * @return the thing, or null if not found
     */
    public ThingDescription getThing(String nodeId) {
        return things.get(nodeId);
    }

    /**
     * Returns the number of registered things.
     *
     * @return the count
     */
    public int size() {
        return things.size();
    }
}
