package ssg.legoflow.rpc.grpc.common;

import ssg.legoflow.rpc.grpc.protobuf.MessageDescriptor;

/**
 * Describes a single gRPC method: its full path, type (unary/streaming),
 * and the request/response message descriptors.
 */
public record MethodDescriptor(
        String fullMethodName,
        String serviceName,
        String methodName,
        MethodType type,
        MessageDescriptor requestDescriptor,
        MessageDescriptor responseDescriptor
) {

    /**
     * Returns the HTTP/2 path for this method: /serviceName/methodName.
     */
    public String path() {
        return "/" + serviceName + "/" + methodName;
    }

    public boolean isClientStreaming() {
        return type == MethodType.CLIENT_STREAMING || type == MethodType.BIDI_STREAMING;
    }

    public boolean isServerStreaming() {
        return type == MethodType.SERVER_STREAMING || type == MethodType.BIDI_STREAMING;
    }

    public static MethodDescriptor unary(String serviceName, String methodName,
                                          MessageDescriptor request, MessageDescriptor response) {
        return new MethodDescriptor(
                serviceName + "/" + methodName, serviceName, methodName,
                MethodType.UNARY, request, response);
    }

    public static MethodDescriptor serverStreaming(String serviceName, String methodName,
                                                    MessageDescriptor request, MessageDescriptor response) {
        return new MethodDescriptor(
                serviceName + "/" + methodName, serviceName, methodName,
                MethodType.SERVER_STREAMING, request, response);
    }

    public static MethodDescriptor clientStreaming(String serviceName, String methodName,
                                                    MessageDescriptor request, MessageDescriptor response) {
        return new MethodDescriptor(
                serviceName + "/" + methodName, serviceName, methodName,
                MethodType.CLIENT_STREAMING, request, response);
    }

    public static MethodDescriptor bidiStreaming(String serviceName, String methodName,
                                                  MessageDescriptor request, MessageDescriptor response) {
        return new MethodDescriptor(
                serviceName + "/" + methodName, serviceName, methodName,
                MethodType.BIDI_STREAMING, request, response);
    }
}
