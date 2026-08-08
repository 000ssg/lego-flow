package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.sensor.SensorData;
import ssg.legoflow.xmpp.iot.sensor.SensorDataRequest;
import ssg.legoflow.xmpp.iot.sensor.SensorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages IoT sensor operations (XEP-0323).
 *
 * <p>Handles sensor registration, data requests, and subscriptions.
 *
 * @since 0.1.0
 */
public class SensorManager {

    private static final Logger LOG = LoggerFactory.getLogger(SensorManager.class);

    private final Map<String, SensorNode> sensors = new ConcurrentHashMap<>();
    private final List<SensorDataListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<SensorDataListener>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Creates a new sensor manager.
     */
    public SensorManager() {
    }

    /**
     * Registers a sensor node.
     *
     * @param node the sensor node to register
     */
    public void registerSensor(SensorNode node) {
        Objects.requireNonNull(node, "node must not be null");
        sensors.put(node.getNodeId(), node);
        LOG.info("Registered sensor: {}", node.getNodeId());
    }

    /**
     * Requests sensor data from a remote node.
     *
     * @param node       the target node JID
     * @param fieldNames the specific field names to request (empty means all)
     * @return a future with the sensor data
     */
    public CompletableFuture<SensorData> requestSensorData(JID node, String... fieldNames) {
        Objects.requireNonNull(node, "node must not be null");
        LOG.info("Requesting sensor data from {} for fields: {}",
                node.toBareJid(), fieldNames.length > 0 ? List.of(fieldNames) : "all");

        // For local sensors, read directly
        String nodeId = node.localpart() != null ? node.localpart() : node.domainpart();
        var localSensor = sensors.get(nodeId);
        if (localSensor != null) {
            return CompletableFuture.completedFuture(localSensor.readSensorData());
        }

        // For remote sensors, return simulated response
        return CompletableFuture.completedFuture(
                new SensorData(nodeId, java.time.Instant.now(), List.of()));
    }

    /**
     * Subscribes to periodic sensor data from a remote node.
     *
     * @param node     the target node JID
     * @param interval the subscription interval
     * @param listener the listener for sensor data updates
     */
    public void subscribeSensorData(JID node, Duration interval, SensorDataListener listener) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        String nodeKey = node.toBareJid();
        subscriptions.computeIfAbsent(nodeKey, k -> new CopyOnWriteArrayList<>()).add(listener);
        LOG.info("Subscribed to sensor data from {} every {}", nodeKey, interval);
    }

    /**
     * Notifies subscribers about new sensor data.
     *
     * @param nodeJid the node JID
     * @param data    the sensor data
     */
    public void notifySensorData(JID nodeJid, SensorData data) {
        String nodeKey = nodeJid.toBareJid();
        var subs = subscriptions.get(nodeKey);
        if (subs != null) {
            for (var listener : subs) {
                listener.onSensorData(data);
            }
        }
        for (var listener : globalListeners) {
            listener.onSensorData(data);
        }
    }

    /**
     * Adds a global sensor data listener.
     *
     * @param listener the listener
     */
    public void addGlobalListener(SensorDataListener listener) {
        globalListeners.add(listener);
    }

    /**
     * Returns all registered sensors.
     *
     * @return the list of sensors
     */
    public List<SensorNode> getSensors() {
        return List.copyOf(sensors.values());
    }

    /**
     * Returns a sensor by node id.
     *
     * @param nodeId the node id
     * @return the sensor node, or null if not found
     */
    public SensorNode getSensor(String nodeId) {
        return sensors.get(nodeId);
    }
}
