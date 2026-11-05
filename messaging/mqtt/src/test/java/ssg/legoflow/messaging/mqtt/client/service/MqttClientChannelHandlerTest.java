package ssg.legoflow.messaging.mqtt.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.messaging.mqtt.transport.MqttPipelineTransport;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class MqttClientChannelHandlerTest {

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException { return 0; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return null; }
    }

    @Test
    void testConnectLatchCountsDown() throws Exception {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);
        var latch = new CountDownLatch(1);
        handler.setConnectLatch(latch);

        assertThat(latch.getCount()).isEqualTo(1);
        handler.onConnect(mockChannel);
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testOnReadRoutesToTransport() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        handler.onRead(mockChannel, ByteBuffer.wrap("test data".getBytes()));
        // Data should be in transport's ring buffer
        assertThat(transport.peek()).isGreaterThan(0);
    }

    @Test
    void testOnReadWithNullData() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        handler.onRead(mockChannel, null);
        handler.onRead(mockChannel, ByteBuffer.allocate(0));
    }

    @Test
    void testOnWriteRoutesToTransport() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        handler.onWrite(mockChannel);
    }

    @Test
    void testGetMqttService() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        assertThat(handler.getMqttService()).isSameAs(service);
    }

    @Test
    void testOnErrorSetsAttribute() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        handler.onError(mockChannel, new RuntimeException("test"));
    }

    @Test
    void testOnDisconnect() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var mockChannel = new MockChannel();
        var transport = new MqttPipelineTransport(mockChannel);
        var handler = new MqttClientChannelHandler(service, transport);

        handler.onDisconnect(mockChannel);
    }
}
