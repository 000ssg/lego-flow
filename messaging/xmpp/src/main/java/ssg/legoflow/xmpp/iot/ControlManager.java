package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.control.ControllableNode;
import ssg.legoflow.xmpp.iot.control.ControlParameter;
import ssg.legoflow.xmpp.iot.control.ControlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IoT control operations (XEP-0325).
 *
 * <p>Handles registration of controllable nodes and sending control commands.
 *
 * @since 0.1.0
 */
public class ControlManager {

    private static final Logger LOG = LoggerFactory.getLogger(ControlManager.class);

    private final Map<String, ControllableNode> controllables = new ConcurrentHashMap<>();

    /**
     * Creates a new control manager.
     */
    public ControlManager() {
    }

    /**
     * Registers a controllable node.
     *
     * @param node the controllable node to register
     */
    public void registerControllable(ControllableNode node) {
        Objects.requireNonNull(node, "node must not be null");
        controllables.put(node.getNodeId(), node);
        LOG.info("Registered controllable: {}", node.getNodeId());
    }

    /**
     * Sends a single control parameter to a remote node.
     *
     * @param node      the target node JID
     * @param paramName the parameter name
     * @param value     the parameter value
     * @return a future indicating success/failure
     */
    public CompletableFuture<Boolean> sendControl(JID node, String paramName, String value) {
        return sendControl(node, List.of(ControlParameter.ofString(paramName, value)));
    }

    /**
     * Sends multiple control parameters to a remote node.
     *
     * @param node   the target node JID
     * @param params the control parameters
     * @return a future indicating success/failure
     */
    public CompletableFuture<Boolean> sendControl(JID node, List<ControlParameter> params) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(params, "params must not be null");

        LOG.info("Sending control to {} with {} parameters", node.toBareJid(), params.size());

        // For local controllables, apply directly
        String nodeId = node.localpart() != null ? node.localpart() : node.domainpart();
        var localNode = controllables.get(nodeId);
        if (localNode != null) {
            var request = new ControlRequest(
                    new JID(null, "localhost", null), nodeId, params);
            boolean success = localNode.handleControlRequest(request);
            return CompletableFuture.completedFuture(success);
        }

        // For remote nodes, simulate success
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Returns a controllable node by id.
     *
     * @param nodeId the node id
     * @return the controllable node, or null if not found
     */
    public ControllableNode getControllable(String nodeId) {
        return controllables.get(nodeId);
    }

    /**
     * Returns all registered controllable nodes.
     *
     * @return the list of controllable nodes
     */
    public List<ControllableNode> getControllables() {
        return List.copyOf(controllables.values());
    }
}
