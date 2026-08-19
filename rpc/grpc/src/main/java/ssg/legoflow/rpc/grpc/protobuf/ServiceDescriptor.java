package ssg.legoflow.rpc.grpc.protobuf;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import java.util.*;
/**
 * Descriptor for a gRPC service, containing method descriptors.
 */
public class ServiceDescriptor {

    private final String fullName;
    private final Map<String, MethodDescriptor> methods;

    private ServiceDescriptor(String fullName, Map<String, MethodDescriptor> methods) {
        this.fullName = fullName;
        this.methods = Collections.unmodifiableMap(methods);
    }

    public String fullName() {
        return fullName;
    }

    /**
     * Returns the simple service name (last segment after the last dot).
     */
    public String simpleName() {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    public MethodDescriptor method(String name) {
        return methods.get(name);
    }

    public Collection<MethodDescriptor> methods() {
        return methods.values();
    }

    public static Builder builder(String fullName) {
        return new Builder(fullName);
    }

    public static class Builder {
        private final String fullName;
        private final Map<String, MethodDescriptor> methods = new LinkedHashMap<>();

        private Builder(String fullName) {
            this.fullName = fullName;
        }

        public Builder addMethod(MethodDescriptor method) {
            methods.put(method.methodName(), method);
            return this;
        }

        public ServiceDescriptor build() {
            return new ServiceDescriptor(fullName, methods);
        }
    }
}
