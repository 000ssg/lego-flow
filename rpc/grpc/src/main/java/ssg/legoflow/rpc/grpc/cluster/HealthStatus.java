package ssg.legoflow.rpc.grpc.cluster;

/**
 * Health status of a gRPC backend.
 *
 * <p>Maps to {@code grpc.health.v1.HealthCheckResponse.ServingStatus}.
 *
 * @since 0.2.0
 */
public enum HealthStatus {
    /** Backend is serving requests. */
    SERVING,

    /** Backend is not serving; exclude from load balancing. */
    NOT_SERVING,

    /** Service unknown to the backend. */
    SERVICE_UNKNOWN,

    /** Health check failed or no response. */
    UNREACHABLE
}
