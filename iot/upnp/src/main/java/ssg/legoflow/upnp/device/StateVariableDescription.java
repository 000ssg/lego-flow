package ssg.legoflow.upnp.device;

import java.util.List;
import java.util.Objects;

/**
 * Describes a state variable of a UPnP service as defined in the SCPD XML.
 *
 * @param name           the state variable name
 * @param dataType       the data type (e.g., "string", "ui4", "boolean")
 * @param sendEvents     whether changes to this variable trigger events
 * @param defaultValue   the default value; may be {@code null}
 * @param allowedValues  list of allowed values; empty if unrestricted
 * @param minimum        the minimum value for numeric types; may be {@code null}
 * @param maximum        the maximum value for numeric types; may be {@code null}
 * @param step           the step increment for numeric types; may be {@code null}
 * @since 1.0.0
 */
public record StateVariableDescription(
        String name,
        String dataType,
        boolean sendEvents,
        String defaultValue,
        List<String> allowedValues,
        String minimum,
        String maximum,
        String step
) {

    /**
     * Creates a new {@code StateVariableDescription} with validation.
     *
     * @throws NullPointerException if {@code name}, {@code dataType}, or {@code allowedValues} is {@code null}
     */
    public StateVariableDescription {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        allowedValues = List.copyOf(allowedValues);
    }

    /**
     * Creates a simple state variable with no restrictions.
     *
     * @param name       the variable name
     * @param dataType   the data type
     * @param sendEvents whether to send events
     * @return a new state variable description
     * @since 1.0.0
     */
    public static StateVariableDescription of(String name, String dataType, boolean sendEvents) {
        return new StateVariableDescription(name, dataType, sendEvents, null, List.of(), null, null, null);
    }

    /**
     * Returns whether this variable has a restricted set of allowed values.
     *
     * @return {@code true} if allowed values are specified
     * @since 1.0.0
     */
    public boolean hasAllowedValues() {
        return !allowedValues.isEmpty();
    }

    /**
     * Returns whether this variable has a numeric range restriction.
     *
     * @return {@code true} if minimum or maximum is specified
     * @since 1.0.0
     */
    public boolean hasRange() {
        return minimum != null || maximum != null;
    }

    /**
     * Serializes this state variable to SCPD XML fragment.
     *
     * @return the XML representation
     * @since 1.0.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<stateVariable sendEvents=\"").append(sendEvents ? "yes" : "no").append("\">");
        sb.append("<name>").append(name).append("</name>");
        sb.append("<dataType>").append(dataType).append("</dataType>");
        if (defaultValue != null) {
            sb.append("<defaultValue>").append(defaultValue).append("</defaultValue>");
        }
        if (!allowedValues.isEmpty()) {
            sb.append("<allowedValueList>");
            for (var value : allowedValues) {
                sb.append("<allowedValue>").append(value).append("</allowedValue>");
            }
            sb.append("</allowedValueList>");
        }
        if (minimum != null || maximum != null || step != null) {
            sb.append("<allowedValueRange>");
            if (minimum != null) {
                sb.append("<minimum>").append(minimum).append("</minimum>");
            }
            if (maximum != null) {
                sb.append("<maximum>").append(maximum).append("</maximum>");
            }
            if (step != null) {
                sb.append("<step>").append(step).append("</step>");
            }
            sb.append("</allowedValueRange>");
        }
        sb.append("</stateVariable>");
        return sb.toString();
    }
}
