package ssg.legoflow.xmpp.stream;

/**
 * Sealed interface for XMPP stream features negotiated during stream setup.
 *
 * @since 0.1.0
 */
public sealed interface StreamFeature
        permits StreamFeature.TlsFeature, StreamFeature.SaslFeature,
                StreamFeature.BindFeature, StreamFeature.SessionFeature,
                StreamFeature.CompressionFeature {

    /**
     * Returns whether this feature is mandatory.
     *
     * @return true if mandatory
     */
    boolean required();

    /**
     * TLS stream feature (STARTTLS).
     *
     * @param required whether TLS is mandatory
     * @since 0.1.0
     */
    record TlsFeature(boolean required) implements StreamFeature {}

    /**
     * SASL authentication feature.
     *
     * @param mechanisms the list of supported SASL mechanisms
     * @param required   whether SASL is mandatory
     * @since 0.1.0
     */
    record SaslFeature(java.util.List<String> mechanisms, boolean required) implements StreamFeature {}

    /**
     * Resource binding feature.
     *
     * @param required whether binding is mandatory
     * @since 0.1.0
     */
    record BindFeature(boolean required) implements StreamFeature {}

    /**
     * Session establishment feature (deprecated in RFC 6121 but still common).
     *
     * @param required whether session is mandatory
     * @since 0.1.0
     */
    record SessionFeature(boolean required) implements StreamFeature {}

    /**
     * Stream compression feature (XEP-0138).
     *
     * @param methods  the supported compression methods
     * @param required whether compression is mandatory
     * @since 0.1.0
     */
    record CompressionFeature(java.util.List<String> methods, boolean required) implements StreamFeature {}
}
