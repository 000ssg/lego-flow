package ssg.legoflow.http.feature;

public enum HttpFeatureCategory {
    CORE,
    TRANSFER,
    CONTENT,
    CACHING,
    CONNECTION,
    ENTITY,
    METADATA,
    SECURITY,
    WEBSOCKET,
    STATIC,
    HTTP2,
    HTTP3,
    /**
     * Cluster-related features: sticky sessions, load balancing, cache coherence.
     *
     * @since 0.2.0
     */
    CLUSTER
}
