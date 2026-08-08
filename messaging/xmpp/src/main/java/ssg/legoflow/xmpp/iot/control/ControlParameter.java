package ssg.legoflow.xmpp.iot.control;

import java.util.Objects;

/**
 * A control parameter for an IoT controllable node (XEP-0325).
 *
 * @param name  the parameter name
 * @param value the parameter value as a string
 * @param type  the parameter data type
 * @since 0.1.0
 */
public record ControlParameter(String name, String value, ControlParameterType type) {

    /**
     * Control parameter data types.
     *
     * @since 0.1.0
     */
    public enum ControlParameterType {
        /** Boolean value. */
        BOOLEAN,
        /** Integer value. */
        INT,
        /** Long integer value. */
        LONG,
        /** Double-precision floating point value. */
        DOUBLE,
        /** Text string value. */
        STRING,
        /** Date/time value. */
        DATE_TIME,
        /** Color value (e.g., hex). */
        COLOR
    }

    /**
     * Constructs a validated control parameter.
     */
    public ControlParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a boolean control parameter.
     *
     * @param name  the parameter name
     * @param value the boolean value
     * @return a new boolean control parameter
     */
    public static ControlParameter ofBoolean(String name, boolean value) {
        return new ControlParameter(name, String.valueOf(value), ControlParameterType.BOOLEAN);
    }

    /**
     * Creates an integer control parameter.
     *
     * @param name  the parameter name
     * @param value the integer value
     * @return a new integer control parameter
     */
    public static ControlParameter ofInt(String name, int value) {
        return new ControlParameter(name, String.valueOf(value), ControlParameterType.INT);
    }

    /**
     * Creates a double control parameter.
     *
     * @param name  the parameter name
     * @param value the double value
     * @return a new double control parameter
     */
    public static ControlParameter ofDouble(String name, double value) {
        return new ControlParameter(name, String.valueOf(value), ControlParameterType.DOUBLE);
    }

    /**
     * Creates a string control parameter.
     *
     * @param name  the parameter name
     * @param value the string value
     * @return a new string control parameter
     */
    public static ControlParameter ofString(String name, String value) {
        return new ControlParameter(name, value, ControlParameterType.STRING);
    }

    /**
     * Serializes this parameter to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        String elementName = switch (type) {
            case BOOLEAN -> "boolean";
            case INT -> "int";
            case LONG -> "long";
            case DOUBLE -> "double";
            case STRING -> "string";
            case DATE_TIME -> "dateTime";
            case COLOR -> "color";
        };
        return "<" + elementName + " name=\"" + name + "\" value=\"" + value + "\"/>";
    }
}
