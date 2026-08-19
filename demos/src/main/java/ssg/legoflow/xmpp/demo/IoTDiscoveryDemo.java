package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.iot.discovery.ThingDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
/**
 * IoT Discovery demo: register things, search by tags, claim ownership.
 *
 * @since 0.1.0
 */
public class IoTDiscoveryDemo {

    private static final Logger LOG = LoggerFactory.getLogger(IoTDiscoveryDemo.class);

    private final XmppClient client;

    /**
     * Creates the IoT discovery demo.
     */
    public IoTDiscoveryDemo() {
        this.client = new XmppClient();
    }

    /**
     * Sets up the client.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);
        client.connect(config).join();
        client.login("discovery-user", "password").join();
    }

    /**
     * Registers sample IoT things.
     */
    public void registerSampleThings() {
        var dm = client.getDiscoveryManager();

        dm.registerThing(new ThingDescription("sensor-001", null,
                "Living Room Temp", "AcmeSensors", "TS-100", "SN001",
                Map.of("type", "sensor", "location", "living-room"), false)).join();

        dm.registerThing(new ThingDescription("sensor-002", null,
                "Kitchen Humidity", "AcmeSensors", "HS-200", "SN002",
                Map.of("type", "sensor", "location", "kitchen"), false)).join();

        dm.registerThing(new ThingDescription("light-001", null,
                "Garden Light", "SmartLights", "SL-300", "SN003",
                Map.of("type", "actuator", "location", "garden"), false)).join();

        dm.registerThing(new ThingDescription("thermostat-001", null,
                "Main Thermostat", "ClimateControl", "TC-400", "SN004",
                Map.of("type", "controller", "location", "hallway"), false)).join();

        LOG.info("Registered 4 sample things");
    }

    /**
     * Searches for things by tags.
     *
     * @param tags the search tags
     * @return the matching things
     */
    public List<ThingDescription> searchThings(Map<String, String> tags) {
        var results = client.getDiscoveryManager().searchThings(tags).join();
        LOG.info("Search for {} returned {} results", tags, results.size());
        return results;
    }

    /**
     * Claims a thing.
     *
     * @param nodeId the node id
     * @return true if claimed successfully
     */
    public boolean claimThing(String nodeId) {
        boolean result = client.getDiscoveryManager().claimThing(nodeId).join();
        LOG.info("Claim {}: {}", nodeId, result);
        return result;
    }

    /** @return the client */
    public XmppClient getClient() { return client; }

    /** Shuts down. */
    public void shutdown() { client.close(); }
}
