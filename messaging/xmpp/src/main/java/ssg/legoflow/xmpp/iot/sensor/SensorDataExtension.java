package ssg.legoflow.xmpp.iot.sensor;

import ssg.legoflow.xmpp.core.XmppExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * XMPP extension for IoT Sensor Data (XEP-0323).
 *
 * <p>Handles the {@code <req>}, {@code <accepted>}, {@code <fields>}, and
 * {@code <done>} elements within the {@code urn:xmpp:iot:sensordata} namespace.
 *
 * @since 0.1.0
 */
public class SensorDataExtension implements XmppExtension {

    /** The XEP-0323 namespace. */
    public static final String NAMESPACE = "urn:xmpp:iot:sensordata";

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "<(numeric|string|boolean|dateTime|enum|duration)\\s([^/]*)/>");
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(\\w+)=\"([^\"]*)\"");

    /**
     * Element type within this extension.
     */
    public enum ElementType {
        /** Sensor data request. */
        REQ,
        /** Request accepted acknowledgment. */
        ACCEPTED,
        /** Sensor data fields. */
        FIELDS,
        /** Sensor data transfer complete. */
        DONE
    }

    private final ElementType elementType;
    private final SensorData sensorData;
    private final String seqNr;

    /**
     * Creates a sensor data extension.
     *
     * @param elementType the element type
     * @param sensorData  the sensor data (may be null for REQ, ACCEPTED, DONE)
     * @param seqNr       the sequence number for request tracking
     */
    public SensorDataExtension(ElementType elementType, SensorData sensorData, String seqNr) {
        this.elementType = elementType;
        this.sensorData = sensorData;
        this.seqNr = seqNr;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return switch (elementType) {
            case REQ -> "req";
            case ACCEPTED -> "accepted";
            case FIELDS -> "fields";
            case DONE -> "done";
        };
    }

    @Override
    public String toXml() {
        return switch (elementType) {
            case REQ -> "<req xmlns=\"" + NAMESPACE + "\"" +
                    (seqNr != null ? " seqnr=\"" + seqNr + "\"" : "") + "/>";
            case ACCEPTED -> "<accepted xmlns=\"" + NAMESPACE + "\"" +
                    (seqNr != null ? " seqnr=\"" + seqNr + "\"" : "") + "/>";
            case FIELDS -> sensorData != null ? sensorData.toXml() : "<fields xmlns=\"" + NAMESPACE + "\"/>";
            case DONE -> "<done xmlns=\"" + NAMESPACE + "\"" +
                    (seqNr != null ? " seqnr=\"" + seqNr + "\"" : "") + "/>";
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
     * Returns the sensor data (for FIELDS type).
     *
     * @return the sensor data
     */
    public SensorData getSensorData() {
        return sensorData;
    }

    /**
     * Returns the sequence number.
     *
     * @return the sequence number
     */
    public String getSeqNr() {
        return seqNr;
    }

    /**
     * Parses sensor fields from an XML string.
     *
     * @param xml the XML string
     * @return the parsed sensor fields
     */
    public static List<SensorField> parseFields(String xml) {
        List<SensorField> fields = new ArrayList<>();
        Matcher matcher = FIELD_PATTERN.matcher(xml);
        while (matcher.find()) {
            String typeStr = matcher.group(1);
            String attrs = matcher.group(2);
            var attrMap = new java.util.HashMap<String, String>();
            Matcher attrMatcher = ATTR_PATTERN.matcher(attrs);
            while (attrMatcher.find()) {
                attrMap.put(attrMatcher.group(1), attrMatcher.group(2));
            }

            SensorField.SensorFieldType fieldType = switch (typeStr) {
                case "numeric" -> SensorField.SensorFieldType.NUMERIC;
                case "string" -> SensorField.SensorFieldType.STRING;
                case "boolean" -> SensorField.SensorFieldType.BOOLEAN;
                case "dateTime" -> SensorField.SensorFieldType.DATE_TIME;
                case "enum" -> SensorField.SensorFieldType.ENUM;
                case "duration" -> SensorField.SensorFieldType.DURATION;
                default -> SensorField.SensorFieldType.STRING;
            };

            fields.add(new SensorField(
                    attrMap.getOrDefault("name", "unknown"),
                    attrMap.getOrDefault("value", ""),
                    fieldType,
                    attrMap.get("unit"),
                    "true".equals(attrMap.get("writable"))));
        }
        return fields;
    }

    /**
     * Creates a FIELDS extension from sensor data.
     *
     * @param data  the sensor data
     * @param seqNr the sequence number
     * @return the extension
     */
    public static SensorDataExtension fields(SensorData data, String seqNr) {
        return new SensorDataExtension(ElementType.FIELDS, data, seqNr);
    }

    /**
     * Creates a REQ extension.
     *
     * @param seqNr the sequence number
     * @return the extension
     */
    public static SensorDataExtension req(String seqNr) {
        return new SensorDataExtension(ElementType.REQ, null, seqNr);
    }

    /**
     * Creates an ACCEPTED extension.
     *
     * @param seqNr the sequence number
     * @return the extension
     */
    public static SensorDataExtension accepted(String seqNr) {
        return new SensorDataExtension(ElementType.ACCEPTED, null, seqNr);
    }

    /**
     * Creates a DONE extension.
     *
     * @param seqNr the sequence number
     * @return the extension
     */
    public static SensorDataExtension done(String seqNr) {
        return new SensorDataExtension(ElementType.DONE, null, seqNr);
    }
}
