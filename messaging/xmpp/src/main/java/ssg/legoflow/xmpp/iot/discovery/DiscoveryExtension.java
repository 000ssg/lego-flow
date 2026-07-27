package ssg.legoflow.xmpp.iot.discovery;

import ssg.legoflow.xmpp.core.XmppExtension;

/**
 * XMPP extension for IoT Discovery (XEP-0347).
 *
 * <p>Handles the {@code <register>}, {@code <claimed>}, {@code <disown>}, and
 * {@code <unregister>} elements within the {@code urn:xmpp:iot:discovery} namespace.
 *
 * @since 1.0.0
 */
public class DiscoveryExtension implements XmppExtension {

    /** The XEP-0347 namespace. */
    public static final String NAMESPACE = "urn:xmpp:iot:discovery";

    /**
     * Element type within this extension.
     */
    public enum ElementType {
        /** Register a thing. */
        REGISTER,
        /** Thing has been claimed. */
        CLAIMED,
        /** Disown a thing. */
        DISOWN,
        /** Unregister a thing. */
        UNREGISTER
    }

    private final ElementType elementType;
    private final ThingDescription thing;

    /**
     * Creates a discovery extension.
     *
     * @param elementType the element type
     * @param thing       the thing description (may be null for DISOWN, UNREGISTER)
     */
    public DiscoveryExtension(ElementType elementType, ThingDescription thing) {
        this.elementType = elementType;
        this.thing = thing;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return switch (elementType) {
            case REGISTER -> "register";
            case CLAIMED -> "claimed";
            case DISOWN -> "disown";
            case UNREGISTER -> "unregister";
        };
    }

    @Override
    public String toXml() {
        return switch (elementType) {
            case REGISTER -> {
                if (thing != null) {
                    yield "<register xmlns=\"" + NAMESPACE + "\">" + thing.toXml() + "</register>";
                }
                yield "<register xmlns=\"" + NAMESPACE + "\"/>";
            }
            case CLAIMED -> {
                if (thing != null) {
                    yield "<claimed xmlns=\"" + NAMESPACE + "\" nodeId=\"" + thing.nodeId() + "\"" +
                            (thing.owner() != null ? " owner=\"" + thing.owner().toBareJid() + "\"" : "") + "/>";
                }
                yield "<claimed xmlns=\"" + NAMESPACE + "\"/>";
            }
            case DISOWN -> {
                if (thing != null) {
                    yield "<disown xmlns=\"" + NAMESPACE + "\" nodeId=\"" + thing.nodeId() + "\"/>";
                }
                yield "<disown xmlns=\"" + NAMESPACE + "\"/>";
            }
            case UNREGISTER -> {
                if (thing != null) {
                    yield "<unregister xmlns=\"" + NAMESPACE + "\" nodeId=\"" + thing.nodeId() + "\"/>";
                }
                yield "<unregister xmlns=\"" + NAMESPACE + "\"/>";
            }
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
     * Returns the thing description.
     *
     * @return the thing
     */
    public ThingDescription getThing() {
        return thing;
    }

    /**
     * Creates a REGISTER extension.
     *
     * @param thing the thing to register
     * @return the extension
     */
    public static DiscoveryExtension register(ThingDescription thing) {
        return new DiscoveryExtension(ElementType.REGISTER, thing);
    }

    /**
     * Creates a CLAIMED extension.
     *
     * @param thing the claimed thing
     * @return the extension
     */
    public static DiscoveryExtension claimed(ThingDescription thing) {
        return new DiscoveryExtension(ElementType.CLAIMED, thing);
    }

    /**
     * Creates a DISOWN extension.
     *
     * @param thing the thing to disown
     * @return the extension
     */
    public static DiscoveryExtension disown(ThingDescription thing) {
        return new DiscoveryExtension(ElementType.DISOWN, thing);
    }

    /**
     * Creates an UNREGISTER extension.
     *
     * @param thing the thing to unregister
     * @return the extension
     */
    public static DiscoveryExtension unregister(ThingDescription thing) {
        return new DiscoveryExtension(ElementType.UNREGISTER, thing);
    }
}
