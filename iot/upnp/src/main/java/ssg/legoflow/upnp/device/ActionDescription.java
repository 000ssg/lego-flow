package ssg.legoflow.upnp.device;

import java.util.List;
import java.util.Objects;

/**
 * Describes a UPnP service action as defined in the SCPD (Service Control Protocol Description) XML.
 *
 * <p>An action has a name and a list of arguments, each of which is either an input
 * or output parameter related to a state variable.
 *
 * @param name      the action name
 * @param arguments the list of argument descriptions
 * @since 0.1.0
 */
public record ActionDescription(String name, List<ArgumentDescription> arguments) {

    /**
     * Creates a new {@code ActionDescription} with validation.
     *
     * @throws NullPointerException if {@code name} or {@code arguments} is {@code null}
     */
    public ActionDescription {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        arguments = List.copyOf(arguments);
    }

    /**
     * Returns only the input arguments of this action.
     *
     * @return an unmodifiable list of input arguments
     * @since 0.1.0
     */
    public List<ArgumentDescription> inputArguments() {
        return arguments.stream().filter(ArgumentDescription::isInput).toList();
    }

    /**
     * Returns only the output arguments of this action.
     *
     * @return an unmodifiable list of output arguments
     * @since 0.1.0
     */
    public List<ArgumentDescription> outputArguments() {
        return arguments.stream().filter(ArgumentDescription::isOutput).toList();
    }

    /**
     * Serializes this action to SCPD XML fragment.
     *
     * @return the XML representation
     * @since 0.1.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<action>");
        sb.append("<name>").append(name).append("</name>");
        if (!arguments.isEmpty()) {
            sb.append("<argumentList>");
            for (var arg : arguments) {
                sb.append(arg.toXml());
            }
            sb.append("</argumentList>");
        }
        sb.append("</action>");
        return sb.toString();
    }
}
