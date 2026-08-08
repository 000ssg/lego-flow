package ssg.legoflow.xmpp.iot.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An IoT controllable node (XEP-0325).
 *
 * <p>Represents a device that can be controlled remotely via XMPP IQ stanzas.
 *
 * @since 0.1.0
 */
public class ControllableNode {

    private static final Logger LOG = LoggerFactory.getLogger(ControllableNode.class);

    private final String nodeId;
    private final Map<String, ControlParameter> parameters = new ConcurrentHashMap<>();
    private final List<ControlListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new controllable node.
     *
     * @param nodeId the node identifier
     */
    public ControllableNode(String nodeId) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
    }

    /**
     * Sets a control parameter value.
     *
     * @param name  the parameter name
     * @param value the parameter value
     */
    public void setParameter(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        var existing = parameters.get(name);
        if (existing != null) {
            parameters.put(name, new ControlParameter(name, value, existing.type()));
        } else {
            parameters.put(name, ControlParameter.ofString(name, value));
        }
        LOG.debug("Set parameter '{}' = '{}' on node {}", name, value, nodeId);
    }

    /**
     * Adds a typed control parameter.
     *
     * @param parameter the parameter to add
     */
    public void addParameter(ControlParameter parameter) {
        Objects.requireNonNull(parameter, "parameter must not be null");
        parameters.put(parameter.name(), parameter);
    }

    /**
     * Returns all control parameters.
     *
     * @return the list of parameters
     */
    public List<ControlParameter> getParameters() {
        return List.copyOf(parameters.values());
    }

    /**
     * Returns a specific parameter by name.
     *
     * @param name the parameter name
     * @return the parameter, or null if not found
     */
    public ControlParameter getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Handles a control request by applying parameters and notifying listeners.
     *
     * @param request the control request
     * @return true if the control was applied successfully
     */
    public boolean handleControlRequest(ControlRequest request) {
        LOG.info("Handling control request for node {} with {} parameters",
                nodeId, request.parameters().size());
        for (var param : request.parameters()) {
            setParameter(param.name(), param.value());
        }
        for (var listener : listeners) {
            listener.onControlRequest(request);
        }
        return true;
    }

    /**
     * Adds a control listener.
     *
     * @param listener the listener
     */
    public void addControlListener(ControlListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a control listener.
     *
     * @param listener the listener
     */
    public void removeControlListener(ControlListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the node identifier.
     *
     * @return the node id
     */
    public String getNodeId() {
        return nodeId;
    }
}
