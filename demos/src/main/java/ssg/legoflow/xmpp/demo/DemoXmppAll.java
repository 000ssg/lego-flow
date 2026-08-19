package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.PresenceStanza.PresenceShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
/**
 * Comprehensive demo of all XMPP module features.
 *
 * <h2>Configuration</h2>
 * <p><b>Preferred (default): In-memory transport</b> -- No external dependencies.
 * Runs anywhere without an XMPP server. Uses the built-in {@link XmppClient}
 * with loopback message delivery for deterministic demos.</p>
 *
 * <p><b>Alternative: External XMPP server (ejabberd, Prosody)</b> -- Set
 * {@link #USE_EXTERNAL}{@code =true} and configure host/domain.
 * Required for TLS/STARTTLS testing, federation, and server-to-server flows.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Messaging -- two clients exchange chat messages</li>
 *   <li>Presence -- available, away, dnd states and subscription</li>
 *   <li>Roster -- contact add, update, remove, and group management</li>
 *   <li>IoT Sensor Data -- register sensors, read values</li>
 *   <li>IoT Control -- controllable thermostat, set parameters</li>
 *   <li>IoT Discovery -- register things, search by tags, claim</li>
 *   <li>Smart Home -- combined sensors, controllers, and automation</li>
 *   <li>PubSub -- node creation, item publish, subscription</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoXmppAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoXmppAll.class);

    /** Set to {@code true} to connect to an external XMPP server. */
    public static boolean USE_EXTERNAL = false;

    /** Domain for external XMPP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_DOMAIN = "localhost";

    private DemoXmppAll() {}

    /**
     * Results from running the full demo.
     *
     * @param messagesDelivered   number of chat messages delivered between clients
     * @param presenceStatesSet   number of distinct presence states demonstrated
     * @param rosterSize          number of contacts in the roster after operations
     * @param sensorFieldCount    total number of sensor fields read
     * @param controlSuccess      true if thermostat control commands succeeded
     * @param discoveryHits       number of things found in discovery search
     * @param automationTriggered true if smart home automation triggered at least one notification
     * @param pubsubItemCount     number of pubsub items published
     */
    public record Results(
            int messagesDelivered,
            int presenceStatesSet,
            int rosterSize,
            int sensorFieldCount,
            boolean controlSuccess,
            int discoveryHits,
            boolean automationTriggered,
            int pubsubItemCount
    ) {}

    /**
     * Runs the comprehensive demo covering all XMPP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        String domain = USE_EXTERNAL ? EXTERNAL_DOMAIN : "demo.local";

        int messages = demoMessaging(domain);
        int presenceStates = demoPresence(domain);
        int rosterSize = demoRoster(domain);
        int sensorFields = demoIoTSensor(domain);
        boolean controlOk = demoIoTControl(domain);
        int discoveryHits = demoIoTDiscovery(domain);
        boolean automation = demoSmartHome(domain);
        int pubsubItems = demoPubSub(domain);

        return new Results(messages, presenceStates, rosterSize, sensorFields,
                controlOk, discoveryHits, automation, pubsubItems);
    }

    // ======================== 1. MESSAGING =================================

    /**
     * Demonstrates two-way chat messaging between Alice and Bob.
     */
    static int demoMessaging(String domain) {
        LOG.info("=== 1. Messaging ===");
        var demo = new SimpleChatDemo();
        demo.setup(domain);

        demo.aliceSays("Hello Bob!");
        demo.bobSays("Hi Alice, how are you?");
        demo.aliceSays("Great, thanks!");

        int aliceCount = demo.getAliceMessages().size();
        int bobCount = demo.getBobMessages().size();
        LOG.info("Messaging: Alice received {}, Bob received {}", aliceCount, bobCount);

        demo.shutdown();
        return aliceCount + bobCount;
    }

    // ======================== 2. PRESENCE ==================================

    /**
     * Demonstrates presence states: available, away, dnd, chat, unavailable.
     */
    static int demoPresence(String domain) {
        LOG.info("=== 2. Presence ===");
        var demo = new PresenceDemo();
        demo.setup(domain);

        demo.demonstratePresenceStates();

        // Simulate receiving a presence update
        var contactJid = JID.parse("contact@" + domain + "/phone");
        demo.receivePresence(contactJid, PresenceShow.AWAY, "On lunch break");

        int receivedCount = demo.getReceivedPresences().size();
        LOG.info("Presence: received {} presence updates", receivedCount);

        demo.shutdown();
        // We demonstrated 5 states: available, away, dnd, chat, unavailable
        return 5;
    }

    // ======================== 3. ROSTER ====================================

    /**
     * Demonstrates roster management: add contacts, update, remove, group filtering.
     */
    static int demoRoster(String domain) {
        LOG.info("=== 3. Roster ===");
        var demo = new RosterDemo();
        demo.setup(domain);

        demo.demonstrateRosterOperations();

        int size = demo.getRoster().size();
        LOG.info("Roster size after operations: {}", size);

        demo.shutdown();
        return size;
    }

    // ======================== 4. IOT SENSOR ================================

    /**
     * Demonstrates IoT sensor data: register sensors, read field values.
     */
    static int demoIoTSensor(String domain) {
        LOG.info("=== 4. IoT Sensor Data ===");
        var demo = new IoTSensorDemo();
        demo.setup(domain);

        // Read temperature sensor
        var tempData = demo.readSensor("temp-sensor-1");
        int tempFields = tempData.fields().size();

        // Read humidity sensor
        var humData = demo.readSensor("humidity-sensor-1");
        int humFields = humData.fields().size();

        // Update and re-read
        demo.updateSensor("temp-sensor-1", "temperature", "25.0");
        var updatedData = demo.readSensor("temp-sensor-1");

        int totalFields = tempFields + humFields;
        LOG.info("IoT Sensor: temp={} fields, humidity={} fields, total={}", tempFields, humFields, totalFields);

        demo.shutdown();
        return totalFields;
    }

    // ======================== 5. IOT CONTROL ================================

    /**
     * Demonstrates IoT control: set thermostat temperature and mode.
     */
    static boolean demoIoTControl(String domain) {
        LOG.info("=== 5. IoT Control ===");
        var demo = new IoTControlDemo();
        demo.setup(domain);

        boolean tempOk = demo.setTemperature(24.5);
        boolean modeOk = demo.setMode("heat");
        boolean enableOk = demo.setEnabled(true);

        int historySize = demo.getControlHistory().size();
        LOG.info("IoT Control: temp={}, mode={}, enable={}, history={}", tempOk, modeOk, enableOk, historySize);

        demo.shutdown();
        return tempOk && modeOk && enableOk;
    }

    // ======================== 6. IOT DISCOVERY ==============================

    /**
     * Demonstrates IoT discovery: register things, search by tags, claim.
     */
    static int demoIoTDiscovery(String domain) {
        LOG.info("=== 6. IoT Discovery ===");
        var demo = new IoTDiscoveryDemo();
        demo.setup(domain);

        demo.registerSampleThings();

        // Search for sensors
        var sensors = demo.searchThings(Map.of("type", "sensor"));
        int sensorCount = sensors.size();

        // Search for actuators
        var actuators = demo.searchThings(Map.of("type", "actuator"));
        int actuatorCount = actuators.size();

        // Claim a sensor
        demo.claimThing("sensor-001");

        int totalHits = sensorCount + actuatorCount;
        LOG.info("IoT Discovery: sensors={}, actuators={}, total={}", sensorCount, actuatorCount, totalHits);

        demo.shutdown();
        return totalHits;
    }

    // ======================== 7. SMART HOME ================================

    /**
     * Demonstrates smart home automation: sensors + controllers + notifications.
     */
    static boolean demoSmartHome(String domain) {
        LOG.info("=== 7. Smart Home ===");
        var demo = new SmartHomeDemo();
        demo.setup(domain);

        // Simulate cold temperature (should trigger heating automation)
        demo.simulateTemperatureChange(18.0);

        // Simulate motion detection (should turn on lights)
        demo.simulateMotion(true);

        // Read all sensors
        var readings = demo.readAllSensors();
        int totalReadings = readings.stream().mapToInt(r -> r.fields().size()).sum();

        int notificationCount = demo.getNotifications().size();
        LOG.info("Smart Home: {} sensor fields, {} notifications", totalReadings, notificationCount);

        demo.shutdown();
        return notificationCount >= 2;
    }

    // ======================== 8. PUBSUB ====================================

    /**
     * Demonstrates Publish-Subscribe (XEP-0060): node management and item publishing.
     */
    static int demoPubSub(String domain) {
        LOG.info("=== 8. PubSub ===");

        var serviceJid = new JID(null, "pubsub." + domain, null);
        var pubsubManager = new ssg.legoflow.xmpp.pubsub.PubSubManager(serviceJid);

        // Create nodes
        pubsubManager.createNode("news.tech");
        pubsubManager.createNode("news.sports");

        // Subscribe
        pubsubManager.subscribe("news.tech", "user@" + domain);

        // Publish items
        pubsubManager.publish("news.tech", "<entry>Tech news 1</entry>", "user@" + domain);
        pubsubManager.publish("news.tech", "<entry>Tech news 2</entry>", "user@" + domain);
        pubsubManager.publish("news.sports", "<entry>Sports update</entry>", "user@" + domain);

        int itemCount = 3;
        LOG.info("PubSub: published {} items across 2 nodes", itemCount);

        return itemCount;
    }
}
