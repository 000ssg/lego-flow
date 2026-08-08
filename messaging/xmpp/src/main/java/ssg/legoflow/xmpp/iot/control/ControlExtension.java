package ssg.legoflow.xmpp.iot.control;

import ssg.legoflow.xmpp.core.XmppExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XMPP extension for IoT Control (XEP-0325).
 *
 * <p>Handles the {@code <set>} and {@code <setResponse>} elements within
 * the {@code urn:xmpp:iot:control} namespace.
 *
 * @since 0.1.0
 */
public class ControlExtension implements XmppExtension {

    /** The XEP-0325 namespace. */
    public static final String NAMESPACE = "urn:xmpp:iot:control";

    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "<(boolean|int|long|double|string|dateTime|color)\\s([^/]*)/>");
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(\\w+)=\"([^\"]*)\"");

    /**
     * Element type within this extension.
     */
    public enum ElementType {
        /** Control set request. */
        SET,
        /** Control set response. */
        SET_RESPONSE
    }

    private final ElementType elementType;
    private final List<ControlParameter> parameters;
    private final boolean success;

    /**
     * Creates a control extension.
     *
     * @param elementType the element type
     * @param parameters  the control parameters
     * @param success     whether the control operation succeeded (for SET_RESPONSE)
     */
    public ControlExtension(ElementType elementType, List<ControlParameter> parameters, boolean success) {
        this.elementType = elementType;
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.success = success;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return switch (elementType) {
            case SET -> "set";
            case SET_RESPONSE -> "setResponse";
        };
    }

    @Override
    public String toXml() {
        return switch (elementType) {
            case SET -> {
                var sb = new StringBuilder();
                sb.append("<set xmlns=\"").append(NAMESPACE).append("\">");
                for (var param : parameters) {
                    sb.append(param.toXml());
                }
                sb.append("</set>");
                yield sb.toString();
            }
            case SET_RESPONSE -> "<setResponse xmlns=\"" + NAMESPACE + "\"" +
                    " responseCode=\"" + (success ? "OK" : "Error") + "\"/>";
        };
    }

    /**
     * Returns the element type.
     *
     * @return the element type
     */
    public ElementType getElementType() {
        return elementType;
    }

    /**
     * Returns the control parameters.
     *
     * @return the parameters
     */
    public List<ControlParameter> getParameters() {
        return parameters;
    }

    /**
     * Returns whether the operation was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Parses control parameters from an XML string.
     *
     * @param xml the XML string
     * @return the parsed control parameters
     */
    public static List<ControlParameter> parseParameters(String xml) {
        List<ControlParameter> params = new ArrayList<>();
        Matcher matcher = PARAM_PATTERN.matcher(xml);
        while (matcher.find()) {
            String typeStr = matcher.group(1);
            String attrs = matcher.group(2);
            var attrMap = new java.util.HashMap<String, String>();
            Matcher attrMatcher = ATTR_PATTERN.matcher(attrs);
            while (attrMatcher.find()) {
                attrMap.put(attrMatcher.group(1), attrMatcher.group(2));
            }

            ControlParameter.ControlParameterType paramType = switch (typeStr) {
                case "boolean" -> ControlParameter.ControlParameterType.BOOLEAN;
                case "int" -> ControlParameter.ControlParameterType.INT;
                case "long" -> ControlParameter.ControlParameterType.LONG;
                case "double" -> ControlParameter.ControlParameterType.DOUBLE;
                case "string" -> ControlParameter.ControlParameterType.STRING;
                case "dateTime" -> ControlParameter.ControlParameterType.DATE_TIME;
                case "color" -> ControlParameter.ControlParameterType.COLOR;
                default -> ControlParameter.ControlParameterType.STRING;
            };

            params.add(new ControlParameter(
                    attrMap.getOrDefault("name", "unknown"),
                    attrMap.getOrDefault("value", ""),
                    paramType));
        }
        return params;
    }

    /**
     * Creates a SET extension.
     *
     * @param parameters the parameters to set
     * @return the extension
     */
    public static ControlExtension set(List<ControlParameter> parameters) {
        return new ControlExtension(ElementType.SET, parameters, false);
    }

    /**
     * Creates a SET_RESPONSE extension.
     *
     * @param success whether the operation succeeded
     * @return the extension
     */
    public static ControlExtension setResponse(boolean success) {
        return new ControlExtension(ElementType.SET_RESPONSE, List.of(), success);
    }
}
