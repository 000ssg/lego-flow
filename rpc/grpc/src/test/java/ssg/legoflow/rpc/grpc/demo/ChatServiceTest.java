package ssg.legoflow.rpc.grpc.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.client.CallOptions;
import ssg.legoflow.rpc.grpc.client.GrpcChannel;
import ssg.legoflow.rpc.grpc.client.GrpcStub;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.server.GrpcServer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ChatServiceTest {

    private GrpcStub stub;

    @BeforeEach
    void setUp() {
        var server = new GrpcServer();
        ChatService.register(server);
        var channel = new GrpcChannel(server);
        stub = new GrpcStub(channel, ChatService.serviceDescriptor());
    }

    @Test
    void testSingleMessage() {
        var msg = new ProtoMessage()
                .setString(1, "alice")
                .setString(2, "hello")
                .setVarint(3, 1000);

        var responses = stub.bidiStreamingCall("Chat", List.of(msg));
        // Each message produces 2 responses: echo + system notification
        assertThat(responses).hasSize(2);
    }

    @Test
    void testEchoContent() {
        var msg = new ProtoMessage()
                .setString(1, "bob")
                .setString(2, "hi there")
                .setVarint(3, 2000);

        var responses = stub.bidiStreamingCall("Chat", List.of(msg));

        // First response is the echo
        var echo = responses.get(0);
        assertThat(echo.getString(1)).isEqualTo("bob");
        assertThat(echo.getString(2)).isEqualTo("hi there");
        assertThat(echo.getVarint(3)).isEqualTo(2000);
    }

    @Test
    void testSystemNotification() {
        var msg = new ProtoMessage()
                .setString(1, "carol")
                .setString(2, "howdy")
                .setVarint(3, 3000);

        var responses = stub.bidiStreamingCall("Chat", List.of(msg));

        var notification = responses.get(1);
        assertThat(notification.getString(1)).isEqualTo("system");
        assertThat(notification.getString(2)).contains("carol");
        assertThat(notification.getString(2)).contains("howdy");
    }

    @Test
    void testMultipleMessages() {
        var messages = List.of(
                new ProtoMessage().setString(1, "alice").setString(2, "msg1").setVarint(3, 100),
                new ProtoMessage().setString(1, "bob").setString(2, "msg2").setVarint(3, 200),
                new ProtoMessage().setString(1, "carol").setString(2, "msg3").setVarint(3, 300)
        );

        var responses = stub.bidiStreamingCall("Chat", messages);
        assertThat(responses).hasSize(6); // 3 messages * 2 responses each
    }

    @Test
    void testTimestampIncrement() {
        var msg = new ProtoMessage()
                .setString(1, "user")
                .setString(2, "text")
                .setVarint(3, 5000);

        var responses = stub.bidiStreamingCall("Chat", List.of(msg));

        assertThat(responses.get(0).getVarint(3)).isEqualTo(5000);
        assertThat(responses.get(1).getVarint(3)).isEqualTo(5001);
    }

    @Test
    void testServiceDescriptor() {
        var desc = ChatService.serviceDescriptor();
        assertThat(desc.fullName()).isEqualTo("demo.Chat");
        assertThat(desc.methods()).hasSize(1);
        assertThat(desc.method("Chat")).isNotNull();
    }
}
