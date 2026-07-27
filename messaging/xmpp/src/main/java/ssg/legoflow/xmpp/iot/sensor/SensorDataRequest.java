package ssg.legoflow.xmpp.iot.sensor;

import ssg.legoflow.xmpp.core.JID;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Request for IoT sensor data (XEP-0323).
 *
 * @param from       the requesting JID
 * @param nodeId     the target sensor node
 * @param fieldNames the specific fields to request (empty means all)
 * @param fromTime   the start time for historical data (may be null)
 * @param toTime     the end time for historical data (may be null)
 * @param historical whether to request historical data
 * @since 1.0.0
 */
public record SensorDataRequest(
        JID from,
        String nodeId,
        List<String> fieldNames,
        Instant fromTime,
        Instant toTime,
        boolean historical
) {

    /**
     * Constructs a validated sensor data request.
     */
    public SensorDataRequest {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        fieldNames = fieldNames != null ? List.copyOf(fieldNames) : List.of();
    }

    /**
     * Serializes this request to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<req xmlns=\"urn:xmpp:iot:sensordata\"");
        sb.append(" nodeId=\"").append(nodeId).append("\"");
        if (historical) {
            sb.append(" historical=\"true\"");
        }
        if (fromTime != null) {
            sb.append(" from=\"").append(fromTime).append("\"");
        }
        if (toTime != null) {
            sb.append(" to=\"").append(toTime).append("\"");
        }
        if (fieldNames.isEmpty()) {
            sb.append("/>");
        } else {
            sb.append(">");
            for (var field : fieldNames) {
                sb.append("<field name=\"").append(field).append("\"/>");
            }
            sb.append("</req>");
        }
        return sb.toString();
    }
}
