package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.ServiceDescriptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for gRPC services. Services are registered by their fully qualified name.
 * Each method within a service has its handler registered by the method's full path.
 */
public class GrpcServiceRegistry {

    private final Map<String, ServiceDescriptor> services = new ConcurrentHashMap<>();
    private final Map<String, UnaryHandler> unaryHandlers = new ConcurrentHashMap<>();
    private final Map<String, StreamHandler.ServerStreaming> serverStreamingHandlers = new ConcurrentHashMap<>();
    private final Map<String, StreamHandler.ClientStreaming> clientStreamingHandlers = new ConcurrentHashMap<>();
    private final Map<String, StreamHandler.BidiStreaming> bidiStreamingHandlers = new ConcurrentHashMap<>();
    private final Map<String, MethodDescriptor> methodDescriptors = new ConcurrentHashMap<>();

    /**
     * Registers a service descriptor.
     */
    public void registerService(ServiceDescriptor service) {
        services.put(service.fullName(), service);
        for (var method : service.methods()) {
            methodDescriptors.put(method.path(), method);
        }
    }

    /**
     * Registers a unary handler for a method path.
     */
    public void registerUnaryHandler(String path, UnaryHandler handler) {
        unaryHandlers.put(path, handler);
    }

    /**
     * Registers a server-streaming handler for a method path.
     */
    public void registerServerStreamingHandler(String path, StreamHandler.ServerStreaming handler) {
        serverStreamingHandlers.put(path, handler);
    }

    /**
     * Registers a client-streaming handler for a method path.
     */
    public void registerClientStreamingHandler(String path, StreamHandler.ClientStreaming handler) {
        clientStreamingHandlers.put(path, handler);
    }

    /**
     * Registers a bidi-streaming handler for a method path.
     */
    public void registerBidiStreamingHandler(String path, StreamHandler.BidiStreaming handler) {
        bidiStreamingHandlers.put(path, handler);
    }

    public UnaryHandler getUnaryHandler(String path) {
        return unaryHandlers.get(path);
    }

    public StreamHandler.ServerStreaming getServerStreamingHandler(String path) {
        return serverStreamingHandlers.get(path);
    }

    public StreamHandler.ClientStreaming getClientStreamingHandler(String path) {
        return clientStreamingHandlers.get(path);
    }

    public StreamHandler.BidiStreaming getBidiStreamingHandler(String path) {
        return bidiStreamingHandlers.get(path);
    }

    public MethodDescriptor getMethodDescriptor(String path) {
        return methodDescriptors.get(path);
    }

    public ServiceDescriptor getService(String fullName) {
        return services.get(fullName);
    }

    public Collection<ServiceDescriptor> services() {
        return Collections.unmodifiableCollection(services.values());
    }

    public boolean hasMethod(String path) {
        return methodDescriptors.containsKey(path);
    }
}
