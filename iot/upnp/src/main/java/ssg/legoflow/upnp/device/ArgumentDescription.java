package ssg.legoflow.upnp.device;

import java.util.Objects;

/**
 * Describes an argument of a UPnP service action as defined in the SCPD XML.
 *
 * @param name                 the argument name
 * @param direction            the argument direction: "in" or "out"
 * @param relatedStateVariable the name of the related state variable
 * @since 1.0.0
 */
public record ArgumentDescription(String name, String direction, String relatedStateVariable) {

    /**
     * Direction constant for input arguments.
     *
     * @since 1.0.0
     */
    public static final String DIRECTION_IN = "in";

    /**
     * Direction constant for output arguments.
     *
     * @since 1.0.0
     */
    public static final String DIRECTION_OUT = "out";

    /**
     * Creates a new {@code ArgumentDescription} with validation.
     *
     * @throws NullPointerException     if any parameter is {@code null}
     * @throws IllegalArgumentException if direction is not "in" or "out"
     */
    public ArgumentDescription {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(relatedStateVariable, "relatedStateVariable must not be null");
        if (!DIRECTION_IN.equals(direction) && !DIRECTION_OUT.equals(direction)) {
            throw new IllegalArgumentException("direction must be 'in' or 'out': " + direction);
        }
    }

    /**
     * Returns whether this is an input argument.
     *
     * @return {@code true} if direction is "in"
     * @since 1.0.0
     */
    public boolean isInput() {
        return DIRECTION_IN.equals(direction);
    }

    /**
     * Returns whether this is an output argument.
     *
     * @return {@code true} if direction is "out"
     * @since 1.0.0
     */
    public boolean isOutput() {
        return DIRECTION_OUT.equals(direction);
    }

    /**
     * Serializes this argument to SCPD XML fragment.
     *
     * @return the XML representation
     * @since 1.0.0
     */
    public String toXml() {
        return "<argument>" +
                "<name>" + name + "</name>" +
                "<direction>" + direction + "</direction>" +
                "<relatedStateVariable>" + relatedStateVariable + "</relatedStateVariable>" +
                "</argument>";
    }
}
