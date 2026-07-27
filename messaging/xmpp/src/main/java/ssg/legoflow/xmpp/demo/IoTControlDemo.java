package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.control.ControllableNode;
import ssg.legoflow.xmpp.iot.control.ControlListener;
import ssg.legoflow.xmpp.iot.control.ControlParameter;
import ssg.legoflow.xmpp.iot.control.ControlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * IoT Control demo: controllable thermostat node, client sets temperature.
 *
 * @since 1.0.0
 */
public class IoTControlDemo {

    private static final Logger LOG = LoggerFactory.getLogger(IoTControlDemo.class);

    private final XmppClient client;
    private final ControllableNode thermostat;
    private final List<ControlRequest> controlHistory = new ArrayList<>();

    /**
     * Creates the IoT control demo.
     */
    public IoTControlDemo() {
        this.client = new XmppClient();
        this.thermostat = new ControllableNode("thermostat-1");
    }

    /**
     * Sets up the client and controllable node.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);
        client.connect(config).join();
        client.login("control-user", "password").join();

        // Configure thermostat
        thermostat.addParameter(ControlParameter.ofDouble("targetTemp", 22.0));
        thermostat.addParameter(ControlParameter.ofBoolean("enabled", true));
        thermostat.addParameter(ControlParameter.ofString("mode", "auto"));

        thermostat.addControlListener(controlHistory::add);

        client.getControlManager().registerControllable(thermostat);
        LOG.info("Set up thermostat controller");
    }

    /**
     * Sets the target temperature.
     *
     * @param temperature the target temperature
     * @return true if successful
     */
    public boolean setTemperature(double temperature) {
        var jid = JID.parse("thermostat-1@localhost");
        var params = List.of(ControlParameter.ofDouble("targetTemp", temperature));
        boolean success = client.getControlManager().sendControl(jid, params).join();
        LOG.info("Set temperature to {}: {}", temperature, success ? "OK" : "Failed");
        return success;
    }

    /**
     * Sets the thermostat mode.
     *
     * @param mode the mode (auto, heat, cool, off)
     * @return true if successful
     */
    public boolean setMode(String mode) {
        var jid = JID.parse("thermostat-1@localhost");
        return client.getControlManager().sendControl(jid, "mode", mode).join();
    }

    /**
     * Enables or disables the thermostat.
     *
     * @param enabled true to enable
     * @return true if successful
     */
    public boolean setEnabled(boolean enabled) {
        var jid = JID.parse("thermostat-1@localhost");
        var params = List.of(ControlParameter.ofBoolean("enabled", enabled));
        return client.getControlManager().sendControl(jid, params).join();
    }

    /** @return control history */
    public List<ControlRequest> getControlHistory() { return List.copyOf(controlHistory); }

    /** @return the thermostat node */
    public ControllableNode getThermostat() { return thermostat; }

    /** @return the client */
    public XmppClient getClient() { return client; }

    /** Shuts down. */
    public void shutdown() { client.close(); }
}
