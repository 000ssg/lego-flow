package ssg.legoflow.xmpp.iot.discovery;

import ssg.legoflow.xmpp.core.JID;

import java.util.Map;
import java.util.Objects;

/**
 * Description of an IoT thing for discovery (XEP-0347).
 *
 * @param nodeId       the unique node identifier
 * @param owner        the JID of the owner (may be null if unclaimed)
 * @param name         the human-readable name
 * @param manufacturer the manufacturer name
 * @param model        the model identifier
 * @param serialNumber the serial number
 * @param tags         additional key-value tags for search
 * @param claimed      whether the thing has been claimed by an owner
 * @since 1.0.0
 */
public record ThingDescription(
        String nodeId,
        JID owner,
        String name,
        String manufacturer,
        String model,
        String serialNumber,
        Map<String, String> tags,
        boolean claimed
) {

    /**
     * Constructs a validated thing description.
     */
    public ThingDescription {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        tags = tags != null ? Map.copyOf(tags) : Map.of();
    }

    /**
     * Returns a new ThingDescription with the specified owner and claimed status.
     *
     * @param owner the new owner
     * @return a claimed thing description
     */
    public ThingDescription withOwner(JID owner) {
        return new ThingDescription(nodeId, owner, name, manufacturer, model, serialNumber, tags, true);
    }

    /**
     * Returns a new ThingDescription without an owner (disowned).
     *
     * @return a disowned thing description
     */
    public ThingDescription disown() {
        return new ThingDescription(nodeId, null, name, manufacturer, model, serialNumber, tags, false);
    }

    /**
     * Serializes this thing description to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<thing xmlns=\"urn:xmpp:iot:discovery\"");
        sb.append(" nodeId=\"").append(nodeId).append("\"");
        if (owner != null) {
            sb.append(" owner=\"").append(owner.toBareJid()).append("\"");
        }
        if (name != null) {
            sb.append(" name=\"").append(name).append("\"");
        }
        if (manufacturer != null) {
            sb.append(" manufacturer=\"").append(manufacturer).append("\"");
        }
        if (model != null) {
            sb.append(" model=\"").append(model).append("\"");
        }
        if (serialNumber != null) {
            sb.append(" serialNumber=\"").append(serialNumber).append("\"");
        }
        if (claimed) {
            sb.append(" claimed=\"true\"");
        }
        if (tags.isEmpty()) {
            sb.append("/>");
        } else {
            sb.append(">");
            for (var entry : tags.entrySet()) {
                sb.append("<tag name=\"").append(entry.getKey())
                        .append("\" value=\"").append(entry.getValue()).append("\"/>");
            }
            sb.append("</thing>");
        }
        return sb.toString();
    }
}
