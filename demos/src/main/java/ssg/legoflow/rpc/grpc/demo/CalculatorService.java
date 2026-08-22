package ssg.legoflow.rpc.grpc.demo;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;
/**
 * Demo calculator service with unary RPCs: add, multiply, divide.
 *
 * CalcRequest:  field 1 = a (double), field 2 = b (double)
 * CalcResponse: field 1 = result (double)
 */
public class CalculatorService {

    public static final String SERVICE_NAME = "demo.Calculator";

    public static final MessageDescriptor REQUEST_DESCRIPTOR =
            MessageDescriptor.builder("CalcRequest")
                    .addField(FieldDescriptor.scalar(1, "a", FieldDescriptor.Type.DOUBLE))
                    .addField(FieldDescriptor.scalar(2, "b", FieldDescriptor.Type.DOUBLE))
                    .build();

    public static final MessageDescriptor RESPONSE_DESCRIPTOR =
            MessageDescriptor.builder("CalcResponse")
                    .addField(FieldDescriptor.scalar(1, "result", FieldDescriptor.Type.DOUBLE))
                    .build();

    public static final MethodDescriptor ADD_METHOD =
            MethodDescriptor.unary(SERVICE_NAME, "Add", REQUEST_DESCRIPTOR, RESPONSE_DESCRIPTOR);

    public static final MethodDescriptor MULTIPLY_METHOD =
            MethodDescriptor.unary(SERVICE_NAME, "Multiply", REQUEST_DESCRIPTOR, RESPONSE_DESCRIPTOR);

    public static final MethodDescriptor DIVIDE_METHOD =
            MethodDescriptor.unary(SERVICE_NAME, "Divide", REQUEST_DESCRIPTOR, RESPONSE_DESCRIPTOR);

    public static ServiceDescriptor serviceDescriptor() {
        return ServiceDescriptor.builder(SERVICE_NAME)
                .addMethod(ADD_METHOD)
                .addMethod(MULTIPLY_METHOD)
                .addMethod(DIVIDE_METHOD)
                .build();
    }

    /**
     * Registers this service with the given server.
     */
    public static void register(GrpcServer server) {
        var registry = server.registry();
        registry.registerService(serviceDescriptor());

        registry.registerUnaryHandler(ADD_METHOD.path(), (request, metadata) -> {
            double a = request.getDouble(1);
            double b = request.getDouble(2);
            return new ProtoMessage().setDouble(1, a + b);
        });

        registry.registerUnaryHandler(MULTIPLY_METHOD.path(), (request, metadata) -> {
            double a = request.getDouble(1);
            double b = request.getDouble(2);
            return new ProtoMessage().setDouble(1, a * b);
        });

        registry.registerUnaryHandler(DIVIDE_METHOD.path(), (request, metadata) -> {
            double a = request.getDouble(1);
            double b = request.getDouble(2);
            if (b == 0.0) {
                throw new StatusException(GrpcStatus.INVALID_ARGUMENT, "Division by zero");
            }
            return new ProtoMessage().setDouble(1, a / b);
        });
    }
}
