package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.discovery.IoTRegistry;
import ssg.legoflow.xmpp.iot.discovery.ThingDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
/**
 * Manages IoT discovery operations (XEP-0347).
 *
 * <p>Handles thing registration, searching, and claiming.
 *
 * @since 0.1.0
 */
public class DiscoveryManager {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryManager.class);

    private final IoTRegistry registry;
    private JID localJid;

    /**
     * Creates a new discovery manager.
     */
    public DiscoveryManager() {
        this.registry = new IoTRegistry();
    }

    /**
     * Creates a new discovery manager with a specific registry.
     *
     * @param registry the registry to use
     */
    public DiscoveryManager(IoTRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Sets the local JID for this manager.
     *
     * @param localJid the local JID
     */
    public void setLocalJid(JID localJid) {
        this.localJid = localJid;
    }

    /**
     * Registers a thing in the discovery registry.
     *
     * @param thing the thing to register
     * @return a future that completes when registration is done
     */
    public CompletableFuture<Void> registerThing(ThingDescription thing) {
        Objects.requireNonNull(thing, "thing must not be null");
        registry.register(thing);
        LOG.info("Registered thing: {} ({})", thing.nodeId(), thing.name());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Searches for things matching the given tags.
     *
     * @param tags the search tags
     * @return a future with the matching things
     */
    public CompletableFuture<List<ThingDescription>> searchThings(Map<String, String> tags) {
        var results = registry.search(tags);
        LOG.info("Search returned {} results for tags: {}", results.size(), tags);
        return CompletableFuture.completedFuture(results);
    }

    /**
     * Claims a thing for the local user.
     *
     * @param nodeId the node identifier
     * @return a future indicating success/failure
     */
    public CompletableFuture<Boolean> claimThing(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        JID owner = localJid != null ? localJid : new JID(null, "localhost", null);
        boolean success = registry.claim(nodeId, owner);
        LOG.info("Claim thing {}: {}", nodeId, success ? "success" : "failed");
        return CompletableFuture.completedFuture(success);
    }

    /**
     * Returns the underlying registry.
     *
     * @return the IoT registry
     */
    public IoTRegistry getRegistry() {
        return registry;
    }
}
