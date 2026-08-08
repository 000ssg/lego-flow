package ssg.legoflow.xmpp.iot.sensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * An IoT sensor node that provides sensor data (XEP-0323).
 *
 * @since 0.1.0
 */
public class SensorNode {

    private static final Logger LOG = LoggerFactory.getLogger(SensorNode.class);

    private final String nodeId;
    private final String sourceId;
    private final List<SensorField> fields = new CopyOnWriteArrayList<>();
    private final List<Consumer<SensorDataRequest>> readListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new sensor node.
     *
     * @param nodeId   the node identifier
     * @param sourceId the source identifier
     */
    public SensorNode(String nodeId, String sourceId) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.sourceId = sourceId;
    }

    /**
     * Reads the current sensor data from this node.
     *
     * @return the current sensor data
     */
    public SensorData readSensorData() {
        LOG.debug("Reading sensor data from node: {}", nodeId);
        return new SensorData(nodeId, Instant.now(), List.copyOf(fields));
    }

    /**
     * Adds a sensor field to this node.
     *
     * @param field the field to add
     */
    public void addField(SensorField field) {
        Objects.requireNonNull(field, "field must not be null");
        fields.add(field);
        LOG.debug("Added field '{}' to node {}", field.name(), nodeId);
    }

    /**
     * Updates a sensor field value.
     *
     * @param name  the field name
     * @param value the new value
     */
    public void updateField(String name, String value) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(name)) {
                var old = fields.get(i);
                fields.set(i, new SensorField(name, value, old.type(), old.unit(), old.writable()));
                LOG.debug("Updated field '{}' on node {} to '{}'", name, nodeId, value);
                return;
            }
        }
    }

    /**
     * Returns all fields of this sensor node.
     *
     * @return the list of fields
     */
    public List<SensorField> getFields() {
        return List.copyOf(fields);
    }

    /**
     * Adds a listener for read requests.
     *
     * @param listener the listener
     */
    public void addReadListener(Consumer<SensorDataRequest> listener) {
        readListeners.add(listener);
    }

    /**
     * Handles a read request.
     *
     * @param request the read request
     */
    public void handleReadRequest(SensorDataRequest request) {
        for (var listener : readListeners) {
            listener.accept(request);
        }
    }

    /**
     * Returns the node identifier.
     *
     * @return the node id
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns the source identifier.
     *
     * @return the source id
     */
    public String getSourceId() {
        return sourceId;
    }
}
