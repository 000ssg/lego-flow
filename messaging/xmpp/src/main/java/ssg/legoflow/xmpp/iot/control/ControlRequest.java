package ssg.legoflow.xmpp.iot.control;

import ssg.legoflow.xmpp.core.JID;
import java.util.List;
import java.util.Objects;
/**
 * A control request for an IoT node (XEP-0325).
 *
 * @param from       the requesting JID
 * @param nodeId     the target node identifier
 * @param parameters the control parameters to set
 * @since 0.1.0
 */
public record ControlRequest(JID from, String nodeId, List<ControlParameter> parameters) {

    /**
     * Constructs a validated control request.
     */
    public ControlRequest {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
    }

    /**
     * Serializes this request to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<set xmlns=\"urn:xmpp:iot:control\"");
        sb.append(" nodeId=\"").append(nodeId).append("\">");
        for (var param : parameters) {
            sb.append(param.toXml());
        }
        sb.append("</set>");
        return sb.toString();
    }
}
