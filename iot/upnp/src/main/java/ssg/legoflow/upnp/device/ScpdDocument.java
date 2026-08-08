package ssg.legoflow.upnp.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents a UPnP Service Control Protocol Description (SCPD) document.
 *
 * <p>An SCPD document defines the actions and state variables exposed by a UPnP service.
 * It is retrieved from the URL specified in the service's {@code SCPDURL} element.
 *
 * @param actions        the list of actions defined by the service
 * @param stateVariables the list of state variables defined by the service
 * @since 0.1.0
 */
public record ScpdDocument(List<ActionDescription> actions, List<StateVariableDescription> stateVariables) {

    private static final Pattern ACTION_PATTERN = Pattern.compile("<action>(.*?)</action>", Pattern.DOTALL);
    private static final Pattern ACTION_NAME_PATTERN = Pattern.compile("<name>(.*?)</name>");
    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("<argument>(.*?)</argument>", Pattern.DOTALL);
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("<direction>(.*?)</direction>");
    private static final Pattern RELATED_VAR_PATTERN = Pattern.compile(
            "<relatedStateVariable>(.*?)</relatedStateVariable>");
    private static final Pattern STATE_VAR_PATTERN = Pattern.compile(
            "<stateVariable\\s+sendEvents=\"(yes|no)\">(.*?)</stateVariable>", Pattern.DOTALL);
    private static final Pattern DATA_TYPE_PATTERN = Pattern.compile("<dataType>(.*?)</dataType>");
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile("<defaultValue>(.*?)</defaultValue>");
    private static final Pattern ALLOWED_VALUE_PATTERN = Pattern.compile("<allowedValue>(.*?)</allowedValue>");
    private static final Pattern MINIMUM_PATTERN = Pattern.compile("<minimum>(.*?)</minimum>");
    private static final Pattern MAXIMUM_PATTERN = Pattern.compile("<maximum>(.*?)</maximum>");
    private static final Pattern STEP_PATTERN = Pattern.compile("<step>(.*?)</step>");

    /**
     * Creates a new {@code ScpdDocument} with validation.
     *
     * @throws NullPointerException if {@code actions} or {@code stateVariables} is {@code null}
     */
    public ScpdDocument {
        Objects.requireNonNull(actions, "actions must not be null");
        Objects.requireNonNull(stateVariables, "stateVariables must not be null");
        actions = List.copyOf(actions);
        stateVariables = List.copyOf(stateVariables);
    }

    /**
     * Finds an action by name.
     *
     * @param name the action name
     * @return the action description, or empty if not found
     * @since 0.1.0
     */
    public Optional<ActionDescription> findAction(String name) {
        return actions.stream().filter(a -> a.name().equals(name)).findFirst();
    }

    /**
     * Finds a state variable by name.
     *
     * @param name the state variable name
     * @return the state variable description, or empty if not found
     * @since 0.1.0
     */
    public Optional<StateVariableDescription> findStateVariable(String name) {
        return stateVariables.stream().filter(v -> v.name().equals(name)).findFirst();
    }

    /**
     * Parses an SCPD document from its XML representation.
     *
     * @param xml the SCPD XML string
     * @return the parsed SCPD document
     * @throws NullPointerException     if {@code xml} is {@code null}
     * @throws IllegalArgumentException if the XML cannot be parsed
     * @since 0.1.0
     */
    public static ScpdDocument parseXml(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");

        var actions = parseActions(xml);
        var stateVariables = parseStateVariables(xml);
        return new ScpdDocument(actions, stateVariables);
    }

    /**
     * Serializes this SCPD document to its XML representation.
     *
     * @return the SCPD XML string
     * @since 0.1.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">");
        sb.append("<specVersion><major>1</major><minor>0</minor></specVersion>");
        sb.append("<actionList>");
        for (var action : actions) {
            sb.append(action.toXml());
        }
        sb.append("</actionList>");
        sb.append("<serviceStateTable>");
        for (var variable : stateVariables) {
            sb.append(variable.toXml());
        }
        sb.append("</serviceStateTable>");
        sb.append("</scpd>");
        return sb.toString();
    }

    private static List<ActionDescription> parseActions(String xml) {
        var result = new ArrayList<ActionDescription>();
        var matcher = ACTION_PATTERN.matcher(xml);
        while (matcher.find()) {
            var actionXml = matcher.group(1);
            var nameMatcher = ACTION_NAME_PATTERN.matcher(actionXml);
            if (!nameMatcher.find()) {
                continue;
            }
            var actionName = nameMatcher.group(1);
            var arguments = parseArguments(actionXml);
            result.add(new ActionDescription(actionName, arguments));
        }
        return result;
    }

    private static List<ArgumentDescription> parseArguments(String actionXml) {
        var result = new ArrayList<ArgumentDescription>();
        var matcher = ARGUMENT_PATTERN.matcher(actionXml);
        while (matcher.find()) {
            var argXml = matcher.group(1);
            var name = extractFirst(ACTION_NAME_PATTERN, argXml);
            var direction = extractFirst(DIRECTION_PATTERN, argXml);
            var relatedVar = extractFirst(RELATED_VAR_PATTERN, argXml);
            if (name != null && direction != null && relatedVar != null) {
                result.add(new ArgumentDescription(name, direction, relatedVar));
            }
        }
        return result;
    }

    private static List<StateVariableDescription> parseStateVariables(String xml) {
        var result = new ArrayList<StateVariableDescription>();
        var matcher = STATE_VAR_PATTERN.matcher(xml);
        while (matcher.find()) {
            var sendEvents = "yes".equals(matcher.group(1));
            var varXml = matcher.group(2);

            var name = extractFirst(ACTION_NAME_PATTERN, varXml);
            var dataType = extractFirst(DATA_TYPE_PATTERN, varXml);
            if (name == null || dataType == null) {
                continue;
            }

            var defaultValue = extractFirst(DEFAULT_VALUE_PATTERN, varXml);
            var allowedValues = extractAll(ALLOWED_VALUE_PATTERN, varXml);
            var minimum = extractFirst(MINIMUM_PATTERN, varXml);
            var maximum = extractFirst(MAXIMUM_PATTERN, varXml);
            var step = extractFirst(STEP_PATTERN, varXml);

            result.add(new StateVariableDescription(name, dataType, sendEvents,
                    defaultValue, allowedValues, minimum, maximum, step));
        }
        return result;
    }

    private static String extractFirst(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> extractAll(Pattern pattern, String text) {
        var result = new ArrayList<String>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}
