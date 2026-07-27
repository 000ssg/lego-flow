package ssg.legoflow.rpc.grpc.common;

/**
 * The four gRPC call types.
 */
public enum MethodType {

    /** Single request, single response. */
    UNARY,

    /** Single request, stream of responses. */
    SERVER_STREAMING,

    /** Stream of requests, single response. */
    CLIENT_STREAMING,

    /** Stream of requests, stream of responses. */
    BIDI_STREAMING
}
