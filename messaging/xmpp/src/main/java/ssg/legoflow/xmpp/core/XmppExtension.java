package ssg.legoflow.xmpp.core;

/**
 * Interface for XMPP stanza extensions.
 *
 * <p>Extensions allow additional namespaced XML elements to be included within stanzas,
 * enabling protocol extensibility as per XMPP's design philosophy.
 *
 * @since 0.1.0
 */
public interface XmppExtension {

    /**
     * Returns the XML namespace URI of this extension.
     *
     * @return the namespace URI
     */
    String getNamespace();

    /**
     * Returns the root element name of this extension.
     *
     * @return the element name
     */
    String getElementName();

    /**
     * Serializes this extension to an XML string.
     *
     * @return the XML representation
     */
    String toXml();
}
