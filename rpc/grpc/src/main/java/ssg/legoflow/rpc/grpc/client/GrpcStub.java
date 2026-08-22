package ssg.legoflow.rpc.grpc.client;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.protobuf.ServiceDescriptor;
import ssg.legoflow.rpc.grpc.transport.GrpcEncoding;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
/**
 * Dynamic stub for making gRPC calls. Wraps a channel and provides
 * convenient methods for all four call types.
 */
public class GrpcStub {

    private final GrpcChannel channel;
    private final ServiceDescriptor serviceDescriptor;
    private final List<ClientInterceptor> interceptors;
    private CallOptions defaultOptions;

    public GrpcStub(GrpcChannel channel, ServiceDescriptor serviceDescriptor) {
        this.channel = channel;
        this.serviceDescriptor = serviceDescriptor;
        this.interceptors = new ArrayList<>();
        this.defaultOptions = CallOptions.defaults();
    }

    public GrpcStub withDeadline(Duration deadline) {
        this.defaultOptions = new CallOptions().deadline(deadline);
        return this;
    }

    public GrpcStub withEncoding(GrpcEncoding encoding) {
        this.defaultOptions = new CallOptions().encoding(encoding);
        return this;
    }

    public GrpcStub withInterceptor(ClientInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    /**
     * Makes a unary call by method name.
     */
    public ProtoMessage unaryCall(String methodName, ProtoMessage request) {
        return unaryCall(methodName, request, defaultOptions);
    }

    public ProtoMessage unaryCall(String methodName, ProtoMessage request, CallOptions options) {
        var method = resolveMethod(methodName);
        options = mergeOptions(options);
        applyInterceptors(method, options);
        return channel.unaryCall(method, request, options);
    }

    /**
     * Makes a server-streaming call by method name.
     */
    public List<ProtoMessage> serverStreamingCall(String methodName, ProtoMessage request) {
        return serverStreamingCall(methodName, request, defaultOptions);
    }

    public List<ProtoMessage> serverStreamingCall(String methodName, ProtoMessage request,
                                                    CallOptions options) {
        var method = resolveMethod(methodName);
        options = mergeOptions(options);
        applyInterceptors(method, options);
        return channel.serverStreamingCall(method, request, options);
    }

    /**
     * Makes a client-streaming call by method name.
     */
    public ProtoMessage clientStreamingCall(String methodName, List<ProtoMessage> requests) {
        return clientStreamingCall(methodName, requests, defaultOptions);
    }

    public ProtoMessage clientStreamingCall(String methodName, List<ProtoMessage> requests,
                                             CallOptions options) {
        var method = resolveMethod(methodName);
        options = mergeOptions(options);
        applyInterceptors(method, options);
        return channel.clientStreamingCall(method, requests, options);
    }

    /**
     * Makes a bidi-streaming call by method name.
     */
    public List<ProtoMessage> bidiStreamingCall(String methodName, List<ProtoMessage> requests) {
        return bidiStreamingCall(methodName, requests, defaultOptions);
    }

    public List<ProtoMessage> bidiStreamingCall(String methodName, List<ProtoMessage> requests,
                                                  CallOptions options) {
        var method = resolveMethod(methodName);
        options = mergeOptions(options);
        applyInterceptors(method, options);
        return channel.bidiStreamingCall(method, requests, options);
    }

    private MethodDescriptor resolveMethod(String methodName) {
        var method = serviceDescriptor.method(methodName);
        if (method == null) {
            throw new IllegalArgumentException(
                    "Method not found: " + methodName + " in service " + serviceDescriptor.fullName());
        }
        return method;
    }

    private CallOptions mergeOptions(CallOptions options) {
        if (options == null) return defaultOptions;
        return options;
    }

    private void applyInterceptors(MethodDescriptor method, CallOptions options) {
        for (var interceptor : interceptors) {
            var call = new ClientCall(method, options);
            interceptor.intercept(method, options, options.metadata(), call);
        }
    }

    public GrpcChannel channel() {
        return channel;
    }

    public ServiceDescriptor serviceDescriptor() {
        return serviceDescriptor;
    }
}
