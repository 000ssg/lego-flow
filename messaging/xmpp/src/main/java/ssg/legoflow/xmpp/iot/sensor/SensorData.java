package ssg.legoflow.xmpp.iot.sensor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * IoT sensor data reading (XEP-0323).
 *
 * @param nodeId    the sensor node identifier
 * @param timestamp the time of the reading
 * @param fields    the sensor data fields
 * @since 1.0.0
 */
public record SensorData(String nodeId, Instant timestamp, List<SensorField> fields) {

    /**
     * Constructs validated sensor data.
     */
    public SensorData {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        fields = fields != null ? List.copyOf(fields) : List.of();
    }

    /**
     * Returns the field with the given name.
     *
     * @param name the field name
     * @return the field, or null if not found
     */
    public SensorField getField(String name) {
        return fields.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns fields of a specific type.
     *
     * @param type the field type
     * @return the matching fields
     */
    public List<SensorField> getFieldsByType(SensorField.SensorFieldType type) {
        return fields.stream()
                .filter(f -> f.type() == type)
                .toList();
    }

    /**
     * Serializes this sensor data to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<fields xmlns=\"urn:xmpp:iot:sensordata\"");
        sb.append(" nodeId=\"").append(nodeId).append("\"");
        sb.append(" timestamp=\"").append(timestamp).append("\">");
        for (var field : fields) {
            sb.append(field.toXml());
        }
        sb.append("</fields>");
        return sb.toString();
    }
}
