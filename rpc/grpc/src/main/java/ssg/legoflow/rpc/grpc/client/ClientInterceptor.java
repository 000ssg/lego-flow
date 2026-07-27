package ssg.legoflow.rpc.grpc.client;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.metadata.Metadata;

/**
 * Interceptor for client-side gRPC calls. Interceptors form a chain
 * and can modify metadata, add logging, etc.
 */
@FunctionalInterface
public interface ClientInterceptor {

    /**
     * Intercepts a client call before it is sent.
     *
     * @param method   the method being called
     * @param options  the call options (may be modified)
     * @param metadata the outgoing metadata (may be modified)
     * @param next     the next interceptor or actual call
     * @return a potentially modified ClientCall
     */
    ClientCall intercept(MethodDescriptor method, CallOptions options, Metadata metadata, ClientCall next);
}
