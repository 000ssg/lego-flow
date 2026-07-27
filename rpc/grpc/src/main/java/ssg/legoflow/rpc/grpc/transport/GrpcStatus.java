package ssg.legoflow.rpc.grpc.transport;

/**
 * All 17 gRPC status codes as defined in the gRPC specification.
 */
public enum GrpcStatus {

    OK(0, "OK"),
    CANCELLED(1, "The operation was cancelled"),
    UNKNOWN(2, "Unknown error"),
    INVALID_ARGUMENT(3, "Invalid argument"),
    DEADLINE_EXCEEDED(4, "Deadline exceeded"),
    NOT_FOUND(5, "Not found"),
    ALREADY_EXISTS(6, "Already exists"),
    PERMISSION_DENIED(7, "Permission denied"),
    RESOURCE_EXHAUSTED(8, "Resource exhausted"),
    FAILED_PRECONDITION(9, "Failed precondition"),
    ABORTED(10, "Aborted"),
    OUT_OF_RANGE(11, "Out of range"),
    UNIMPLEMENTED(12, "Unimplemented"),
    INTERNAL(13, "Internal error"),
    UNAVAILABLE(14, "Unavailable"),
    DATA_LOSS(15, "Data loss"),
    UNAUTHENTICATED(16, "Unauthenticated");

    private final int code;
    private final String description;

    GrpcStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static GrpcStatus fromCode(int code) {
        for (var status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown gRPC status code: " + code);
    }

    public boolean isOk() {
        return this == OK;
    }

    public boolean isError() {
        return this != OK;
    }
}
