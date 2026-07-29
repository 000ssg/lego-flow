package ssg.legoflow.rpc.grpc.demo;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;

/**
 * Demo bidi-streaming service: chat room.
 * Each incoming message gets an echo response plus a system notification.
 *
 * ChatMessage: field 1 = sender (string), field 2 = text (string), field 3 = timestamp (int64)
 */
public class ChatService {

    public static final String SERVICE_NAME = "demo.Chat";

    public static final MessageDescriptor MESSAGE_DESCRIPTOR =
            MessageDescriptor.builder("ChatMessage")
                    .addField(FieldDescriptor.scalar(1, "sender", FieldDescriptor.Type.STRING))
                    .addField(FieldDescriptor.scalar(2, "text", FieldDescriptor.Type.STRING))
                    .addField(FieldDescriptor.scalar(3, "timestamp", FieldDescriptor.Type.INT64))
                    .build();

    public static final MethodDescriptor CHAT_METHOD =
            MethodDescriptor.bidiStreaming(SERVICE_NAME, "Chat",
                    MESSAGE_DESCRIPTOR, MESSAGE_DESCRIPTOR);

    public static ServiceDescriptor serviceDescriptor() {
        return ServiceDescriptor.builder(SERVICE_NAME)
                .addMethod(CHAT_METHOD)
                .build();
    }

    /**
     * Registers this service with the given server.
     * For each incoming message, sends back an echo and a system notification.
     */
    public static void register(GrpcServer server) {
        var registry = server.registry();
        registry.registerService(serviceDescriptor());

        registry.registerBidiStreamingHandler(CHAT_METHOD.path(), (requests, metadata, responseStream) -> {
            for (var msg : requests) {
                String sender = msg.getString(1);
                String text = msg.getString(2);
                long timestamp = msg.getVarint(3);

                // Echo the message back
                var echo = new ProtoMessage()
                        .setString(1, sender)
                        .setString(2, text)
                        .setVarint(3, timestamp);
                responseStream.accept(echo);

                // System notification
                var notification = new ProtoMessage()
                        .setString(1, "system")
                        .setString(2, sender + " said: " + text)
                        .setVarint(3, timestamp + 1);
                responseStream.accept(notification);
            }
        });
    }
}
