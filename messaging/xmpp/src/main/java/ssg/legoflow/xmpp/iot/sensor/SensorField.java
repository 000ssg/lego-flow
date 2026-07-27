package ssg.legoflow.xmpp.iot.sensor;

import java.util.Objects;

/**
 * A field within IoT sensor data (XEP-0323).
 *
 * @param name     the field name
 * @param value    the field value as a string
 * @param type     the field data type
 * @param unit     the unit of measurement (may be null)
 * @param writable whether this field can be written to
 * @since 1.0.0
 */
public record SensorField(String name, String value, SensorFieldType type, String unit, boolean writable) {

    /**
     * Sensor field data types.
     *
     * @since 1.0.0
     */
    public enum SensorFieldType {
        /** Numeric value (integer or floating point). */
        NUMERIC,
        /** Text string value. */
        STRING,
        /** Boolean value (true/false). */
        BOOLEAN,
        /** Date/time value. */
        DATE_TIME,
        /** Enumerated value from a predefined set. */
        ENUM,
        /** Duration value. */
        DURATION
    }

    /**
     * Constructs a validated sensor field.
     */
    public SensorField {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a numeric sensor field.
     *
     * @param name  the field name
     * @param value the numeric value
     * @param unit  the unit of measurement
     * @return a new numeric sensor field
     */
    public static SensorField numeric(String name, double value, String unit) {
        return new SensorField(name, String.valueOf(value), SensorFieldType.NUMERIC, unit, false);
    }

    /**
     * Creates a boolean sensor field.
     *
     * @param name  the field name
     * @param value the boolean value
     * @return a new boolean sensor field
     */
    public static SensorField bool(String name, boolean value) {
        return new SensorField(name, String.valueOf(value), SensorFieldType.BOOLEAN, null, false);
    }

    /**
     * Creates a string sensor field.
     *
     * @param name  the field name
     * @param value the string value
     * @return a new string sensor field
     */
    public static SensorField string(String name, String value) {
        return new SensorField(name, value, SensorFieldType.STRING, null, false);
    }

    /**
     * Serializes this field to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        String elementName = switch (type) {
            case NUMERIC -> "numeric";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case DATE_TIME -> "dateTime";
            case ENUM -> "enum";
            case DURATION -> "duration";
        };
        sb.append("<").append(elementName);
        sb.append(" name=\"").append(name).append("\"");
        sb.append(" value=\"").append(value).append("\"");
        if (unit != null) {
            sb.append(" unit=\"").append(unit).append("\"");
        }
        if (writable) {
            sb.append(" writable=\"true\"");
        }
        sb.append("/>");
        return sb.toString();
    }
}
