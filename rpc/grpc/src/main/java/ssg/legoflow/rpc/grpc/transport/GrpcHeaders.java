package ssg.legoflow.rpc.grpc.transport;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.rpc.grpc.metadata.Metadata;

/**
 * Standard gRPC headers and trailers, plus utilities for creating them.
 */
public final class GrpcHeaders {

    // Standard gRPC headers
    public static final String CONTENT_TYPE = "content-type";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";
    public static final String GRPC_PROTO_CONTENT_TYPE = "application/grpc+proto";
    public static final String TE = "te";
    public static final String TE_TRAILERS = "trailers";
    public static final String GRPC_ENCODING = "grpc-encoding";
    public static final String GRPC_ACCEPT_ENCODING = "grpc-accept-encoding";
    public static final String GRPC_TIMEOUT = "grpc-timeout";
    public static final String USER_AGENT = "user-agent";

    // Standard gRPC trailers
    public static final String GRPC_STATUS = "grpc-status";
    public static final String GRPC_MESSAGE = "grpc-message";

    // Pseudo-headers
    public static final String METHOD = ":method";
    public static final String PATH = ":path";
    public static final String SCHEME = ":scheme";
    public static final String AUTHORITY = ":authority";
    public static final String STATUS = ":status";

    private GrpcHeaders() {
    }

    /**
     * Creates HTTP/2 request headers for a gRPC call.
     */
    public static HttpHeaders createRequestHeaders(String path, String authority,
                                                     GrpcEncoding encoding, GrpcTimeout timeout,
                                                     Metadata metadata) {
        var headers = new HttpHeaders();
        headers.set(METHOD, "POST");
        headers.set(PATH, path);
        headers.set(SCHEME, "http");
        if (authority != null) {
            headers.set(AUTHORITY, authority);
        }
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        headers.set(TE, TE_TRAILERS);

        if (encoding != null && encoding != GrpcEncoding.IDENTITY) {
            headers.set(GRPC_ENCODING, encoding.value());
        }
        if (timeout != null) {
            headers.set(GRPC_TIMEOUT, timeout.encode());
        }

        if (metadata != null) {
            for (var key : metadata.keys()) {
                for (var value : metadata.getAll(key)) {
                    headers.add(key, value);
                }
            }
        }

        return headers;
    }

    /**
     * Creates HTTP/2 response headers for a gRPC response.
     */
    public static HttpHeaders createResponseHeaders(GrpcEncoding encoding) {
        var headers = new HttpHeaders();
        headers.set(STATUS, "200");
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        if (encoding != null && encoding != GrpcEncoding.IDENTITY) {
            headers.set(GRPC_ENCODING, encoding.value());
        }
        return headers;
    }

    /**
     * Creates trailers for a gRPC response.
     */
    public static HttpHeaders createTrailers(GrpcStatus status, String message, Metadata metadata) {
        var headers = new HttpHeaders();
        headers.set(GRPC_STATUS, String.valueOf(status.code()));
        if (message != null && !message.isEmpty()) {
            headers.set(GRPC_MESSAGE, percentEncode(message));
        }
        if (metadata != null) {
            for (var key : metadata.keys()) {
                for (var value : metadata.getAll(key)) {
                    headers.add(key, value);
                }
            }
        }
        return headers;
    }

    /**
     * Extracts the gRPC status from trailer headers.
     */
    public static GrpcStatus extractStatus(HttpHeaders trailers) {
        String statusStr = trailers.get(GRPC_STATUS);
        if (statusStr == null) {
            return GrpcStatus.UNKNOWN;
        }
        return GrpcStatus.fromCode(Integer.parseInt(statusStr));
    }

    /**
     * Extracts the gRPC status message from trailer headers.
     */
    public static String extractMessage(HttpHeaders trailers) {
        String message = trailers.get(GRPC_MESSAGE);
        return message != null ? percentDecode(message) : null;
    }

    /**
     * Extracts custom metadata from headers/trailers, excluding gRPC-specific ones.
     */
    public static Metadata extractMetadata(HttpHeaders headers) {
        var metadata = new Metadata();
        for (String name : headers.names()) {
            if (name.startsWith(":") || name.equals(CONTENT_TYPE) || name.equals(TE)
                    || name.equals(GRPC_ENCODING) || name.equals(GRPC_TIMEOUT)
                    || name.equals(GRPC_STATUS) || name.equals(GRPC_MESSAGE)
                    || name.equals(GRPC_ACCEPT_ENCODING)) {
                continue;
            }
            for (String value : headers.getAll(name)) {
                metadata.put(name, value);
            }
        }
        return metadata;
    }

    /**
     * Checks if the content-type is a valid gRPC content type.
     */
    public static boolean isGrpcContentType(String contentType) {
        if (contentType == null) return false;
        return contentType.equals(GRPC_CONTENT_TYPE)
                || contentType.equals(GRPC_PROTO_CONTENT_TYPE)
                || contentType.startsWith("application/grpc+");
    }

    /**
     * Percent-encodes a gRPC status message per the gRPC spec.
     */
    public static String percentEncode(String value) {
        var sb = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '_' || b == '.' || b == '~') {
                sb.append((char) b);
            } else {
                sb.append('%');
                sb.append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    /**
     * Percent-decodes a gRPC status message.
     */
    public static String percentDecode(String value) {
        var baos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    baos.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            baos.write((byte) c);
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
