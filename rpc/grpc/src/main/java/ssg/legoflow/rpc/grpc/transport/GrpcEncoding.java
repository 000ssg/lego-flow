package ssg.legoflow.rpc.grpc.transport;

/**
 * Supported gRPC message compression encodings.
 */
public enum GrpcEncoding {

    IDENTITY("identity"),
    GZIP("gzip"),
    DEFLATE("deflate");

    private final String value;

    GrpcEncoding(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static GrpcEncoding fromValue(String value) {
        if (value == null || value.isEmpty()) return IDENTITY;
        return switch (value.toLowerCase()) {
            case "gzip" -> GZIP;
            case "deflate" -> DEFLATE;
            case "identity" -> IDENTITY;
            default -> throw new IllegalArgumentException("Unsupported encoding: " + value);
        };
    }
}
