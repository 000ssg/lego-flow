package ssg.legoflow.upnp.gena;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a GENA event notification message.
 *
 * <p>Event messages are sent by UPnP services via HTTP NOTIFY to subscribed
 * callback URLs. Each message carries the subscription ID, a sequence number,
 * and a set of changed state variables.
 *
 * @param sid              the subscription ID
 * @param seq              the event sequence number (0 for initial event, then incrementing)
 * @param changedVariables the map of changed state variable names to their new values
 * @since 1.0.0
 */
public record EventMessage(String sid, long seq, Map<String, String> changedVariables) {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "<e:property>(.*?)</e:property>", Pattern.DOTALL);
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<(\\w+)>([^<]*)</\\1>");

    /**
     * Creates a new {@code EventMessage} with validation.
     *
     * @throws NullPointerException     if {@code sid} or {@code changedVariables} is {@code null}
     * @throws IllegalArgumentException if {@code seq} is negative
     */
    public EventMessage {
        Objects.requireNonNull(sid, "sid must not be null");
        Objects.requireNonNull(changedVariables, "changedVariables must not be null");
        if (seq < 0) {
            throw new IllegalArgumentException("seq must not be negative: " + seq);
        }
        changedVariables = Map.copyOf(changedVariables);
    }

    /**
     * Returns whether this is the initial event (sequence number 0).
     *
     * @return {@code true} if this is the initial state notification
     * @since 1.0.0
     */
    public boolean isInitialEvent() {
        return seq == 0;
    }

    /**
     * Parses a GENA event notification from its XML body.
     *
     * <p>The XML follows the UPnP property set format:
     * <pre>{@code
     * <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
     *   <e:property><VarName>value</VarName></e:property>
     * </e:propertyset>
     * }</pre>
     *
     * @param sid the subscription ID from the SID header
     * @param seq the sequence number from the SEQ header
     * @param xml the event XML body
     * @return the parsed event message
     * @throws NullPointerException if any parameter is {@code null}
     * @since 1.0.0
     */
    public static EventMessage parseXml(String sid, long seq, String xml) {
        Objects.requireNonNull(sid, "sid must not be null");
        Objects.requireNonNull(xml, "xml must not be null");

        var variables = new LinkedHashMap<String, String>();
        var propMatcher = PROPERTY_PATTERN.matcher(xml);
        while (propMatcher.find()) {
            var propXml = propMatcher.group(1);
            var elemMatcher = ELEMENT_PATTERN.matcher(propXml);
            while (elemMatcher.find()) {
                variables.put(elemMatcher.group(1), elemMatcher.group(2));
            }
        }

        return new EventMessage(sid, seq, variables);
    }

    /**
     * Serializes this event message to UPnP event XML format.
     *
     * @return the XML representation of the event
     * @since 1.0.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">");
        for (var entry : changedVariables.entrySet()) {
            sb.append("<e:property>");
            sb.append("<").append(entry.getKey()).append(">");
            sb.append(escapeXml(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">");
            sb.append("</e:property>");
        }
        sb.append("</e:propertyset>");
        return sb.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
