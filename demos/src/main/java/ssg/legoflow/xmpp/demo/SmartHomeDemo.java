package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import ssg.legoflow.xmpp.iot.control.ControllableNode;
import ssg.legoflow.xmpp.iot.control.ControlParameter;
import ssg.legoflow.xmpp.iot.discovery.ThingDescription;
import ssg.legoflow.xmpp.iot.sensor.SensorData;
import ssg.legoflow.xmpp.iot.sensor.SensorField;
import ssg.legoflow.xmpp.iot.sensor.SensorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Complete smart home demo combining sensors, controllers, discovery, and chat notifications.
 *
 * @since 0.1.0
 */
public class SmartHomeDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SmartHomeDemo.class);

    private final XmppClient hubClient;
    private final XmppClient userClient;
    private final SensorNode tempSensor;
    private final SensorNode motionSensor;
    private final ControllableNode thermostat;
    private final ControllableNode lights;
    private final List<MessageStanza> notifications = new ArrayList<>();

    /**
     * Creates the smart home demo.
     */
    public SmartHomeDemo() {
        this.hubClient = new XmppClient();
        this.userClient = new XmppClient();
        this.tempSensor = new SensorNode("home-temp", "smart-home");
        this.motionSensor = new SensorNode("home-motion", "smart-home");
        this.thermostat = new ControllableNode("home-thermostat");
        this.lights = new ControllableNode("home-lights");
    }

    /**
     * Sets up the complete smart home.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);

        // Hub client manages devices
        hubClient.connect(config).join();
        hubClient.login("hub", "password").join();

        // User client receives notifications
        userClient.connect(config).join();
        userClient.login("homeowner", "password").join();
        userClient.addMessageListener(notifications::add);

        // Configure sensors
        tempSensor.addField(SensorField.numeric("temperature", 21.0, "C"));
        tempSensor.addField(SensorField.numeric("humidity", 50.0, "%"));
        motionSensor.addField(SensorField.bool("motion", false));
        motionSensor.addField(SensorField.string("lastDetection", "none"));

        hubClient.getSensorManager().registerSensor(tempSensor);
        hubClient.getSensorManager().registerSensor(motionSensor);

        // Configure controllers
        thermostat.addParameter(ControlParameter.ofDouble("targetTemp", 22.0));
        thermostat.addParameter(ControlParameter.ofBoolean("enabled", true));
        lights.addParameter(ControlParameter.ofBoolean("on", false));
        lights.addParameter(ControlParameter.ofInt("brightness", 50));

        hubClient.getControlManager().registerControllable(thermostat);
        hubClient.getControlManager().registerControllable(lights);

        // Register in discovery
        var dm = hubClient.getDiscoveryManager();
        dm.registerThing(new ThingDescription("home-temp", null,
                "Home Temperature", "SmartHome", "TH-1", "SN-T1",
                Map.of("type", "sensor", "room", "living-room"), false)).join();
        dm.registerThing(new ThingDescription("home-motion", null,
                "Motion Detector", "SmartHome", "MD-1", "SN-M1",
                Map.of("type", "sensor", "room", "entrance"), false)).join();
        dm.registerThing(new ThingDescription("home-thermostat", null,
                "Smart Thermostat", "SmartHome", "TC-1", "SN-C1",
                Map.of("type", "controller", "room", "hallway"), false)).join();
        dm.registerThing(new ThingDescription("home-lights", null,
                "Smart Lights", "SmartHome", "SL-1", "SN-L1",
                Map.of("type", "controller", "room", "living-room"), false)).join();

        LOG.info("Smart home set up with 2 sensors, 2 controllers");
    }

    /**
     * Simulates a temperature change triggering an automation.
     *
     * @param newTemp the new temperature reading
     */
    public void simulateTemperatureChange(double newTemp) {
        tempSensor.updateField("temperature", String.valueOf(newTemp));
        var data = tempSensor.readSensorData();
        LOG.info("Temperature changed to {}C", newTemp);

        // Auto-adjust thermostat if too cold/hot
        if (newTemp < 20.0) {
            var jid = JID.parse("home-thermostat@localhost");
            hubClient.getControlManager().sendControl(jid,
                    List.of(ControlParameter.ofDouble("targetTemp", 23.0))).join();
            sendNotification("Temperature dropped to " + newTemp + "C, thermostat set to 23C");
        } else if (newTemp > 26.0) {
            var jid = JID.parse("home-thermostat@localhost");
            hubClient.getControlManager().sendControl(jid,
                    List.of(ControlParameter.ofDouble("targetTemp", 22.0))).join();
            sendNotification("Temperature rose to " + newTemp + "C, thermostat set to 22C");
        }
    }

    /**
     * Simulates motion detection.
     *
     * @param detected whether motion was detected
     */
    public void simulateMotion(boolean detected) {
        motionSensor.updateField("motion", String.valueOf(detected));
        if (detected) {
            motionSensor.updateField("lastDetection", java.time.Instant.now().toString());
            // Turn on lights
            var jid = JID.parse("home-lights@localhost");
            hubClient.getControlManager().sendControl(jid,
                    List.of(ControlParameter.ofBoolean("on", true),
                            ControlParameter.ofInt("brightness", 100))).join();
            sendNotification("Motion detected at entrance, lights turned on");
        }
    }

    /**
     * Reads all sensor data from the hub.
     *
     * @return list of sensor data readings
     */
    public List<SensorData> readAllSensors() {
        var readings = new ArrayList<SensorData>();
        readings.add(tempSensor.readSensorData());
        readings.add(motionSensor.readSensorData());
        return readings;
    }

    private void sendNotification(String message) {
        hubClient.sendMessage(userClient.getLocalJid(), message);
        var msg = MessageStanza.chat("notif-" + System.nanoTime(),
                hubClient.getLocalJid(), userClient.getLocalJid(), message);
        userClient.handleStanza(msg);
        LOG.info("Notification: {}", message);
    }

    /** @return received notifications */
    public List<MessageStanza> getNotifications() { return List.copyOf(notifications); }

    /** @return the hub client */
    public XmppClient getHubClient() { return hubClient; }

    /** @return the user client */
    public XmppClient getUserClient() { return userClient; }

    /** @return the temperature sensor */
    public SensorNode getTempSensor() { return tempSensor; }

    /** @return the motion sensor */
    public SensorNode getMotionSensor() { return motionSensor; }

    /** @return the thermostat */
    public ControllableNode getThermostat() { return thermostat; }

    /** @return the lights */
    public ControllableNode getLights() { return lights; }

    /** Shuts down all clients. */
    public void shutdown() {
        hubClient.close();
        userClient.close();
    }
}
