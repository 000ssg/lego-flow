package ssg.legoflow.rpc.grpc.common;

import ssg.legoflow.rpc.grpc.transport.GrpcStatus;
import ssg.legoflow.rpc.grpc.metadata.Metadata;

/**
 * Exception carrying a gRPC status code and optional trailing metadata.
 */
public class StatusException extends RuntimeException {

    private final GrpcStatus status;
    private final Metadata trailers;

    public StatusException(GrpcStatus status) {
        super(status.name());
        this.status = status;
        this.trailers = new Metadata();
    }

    public StatusException(GrpcStatus status, String message) {
        super(message);
        this.status = status;
        this.trailers = new Metadata();
    }

    public StatusException(GrpcStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.trailers = new Metadata();
    }

    public StatusException(GrpcStatus status, String message, Metadata trailers) {
        super(message);
        this.status = status;
        this.trailers = trailers != null ? trailers : new Metadata();
    }

    public GrpcStatus status() {
        return status;
    }

    public Metadata trailers() {
        return trailers;
    }
}
