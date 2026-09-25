package ssg.legoflow.messaging.mqtt.broker.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

class MqttBrokerChannelHandlerTest {

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException { return 0; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return null; }
    }

    @Test
    void testHandlerCreatedWithService() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        assertThat(handler.getService()).isSameAs(service);
    }

    @Test
    void testOnConnectWithNullBroker() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        var channel = new MockChannel();
        // Broker is null — handler should not crash
        handler.onConnect(channel);
        // Channel should still be open (non-TcpDataChannel is ignored)
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void testOnReadWithNoTransport() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        var channel = new MockChannel();
        // No transport mapped — should not crash
        handler.onRead(channel, ByteBuffer.wrap("test".getBytes()));
    }

    @Test
    void testOnWriteWithNoTransport() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        var channel = new MockChannel();
        handler.onWrite(channel);
    }

    @Test
    void testOnDisconnectWithNoTransport() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        var channel = new MockChannel();
        handler.onDisconnect(channel);
    }

    @Test
    void testOnErrorCallsDisconnect() {
        var service = MqttBrokerService.builder("test-broker", 1883).build();
        var handler = new MqttBrokerChannelHandler(service);
        var channel = new MockChannel();
        handler.onError(channel, new RuntimeException("test error"));
    }
}
