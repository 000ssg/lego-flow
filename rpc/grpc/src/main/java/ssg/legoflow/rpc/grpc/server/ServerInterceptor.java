package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.metadata.Metadata;

/**
 * Interceptor for server-side gRPC calls. Interceptors form a chain
 * and can modify metadata, add logging, authentication, etc.
 */
@FunctionalInterface
public interface ServerInterceptor {

    /**
     * Intercepts a server call before the handler is invoked.
     *
     * @param method   the method being called
     * @param metadata the request metadata
     * @param next     the next interceptor or actual handler
     * @return a potentially modified ServerCall
     */
    ServerCall intercept(MethodDescriptor method, Metadata metadata, ServerCall next);
}
