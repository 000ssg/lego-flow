package ssg.legoflow.messaging.kafka.protocol;

/**
 * ApiVersions request (API key 18) for version negotiation.
 *
 * @param clientSoftwareName    the client software name (nullable)
 * @param clientSoftwareVersion the client software version (nullable)
 * @since 0.1.0
 */
public record ApiVersionsRequest(String clientSoftwareName, String clientSoftwareVersion) {

    /** Creates an empty ApiVersions request. */
    public ApiVersionsRequest() {
        this(null, null);
    }
}
