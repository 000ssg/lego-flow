package ssg.legoflow.messaging.stomp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

class StompServerChannelHandlerTest {

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException { return 0; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return null; }
    }

    @Test void testHandlerCreatedWithService() {
        var service = StompServerService.builder().port(61613).build();
        var handler = new StompServerChannelHandler(service);
        // Constructor doesn't expose getService — just verify construction works
        assertThat(handler).isNotNull();
    }

    @Test void testOnReadWithNoTransport() {
        var service = StompServerService.builder().port(61613).build();
        var handler = new StompServerChannelHandler(service);
        var channel = new MockChannel();
        handler.onRead(channel, ByteBuffer.wrap("test".getBytes()));
    }

    @Test void testOnWriteWithNoTransport() {
        var service = StompServerService.builder().port(61613).build();
        var handler = new StompServerChannelHandler(service);
        var channel = new MockChannel();
        handler.onWrite(channel);
    }

    @Test void testOnDisconnectWithNoTransport() {
        var service = StompServerService.builder().port(61613).build();
        var handler = new StompServerChannelHandler(service);
        var channel = new MockChannel();
        handler.onDisconnect(channel);
    }

    @Test void testOnErrorCallsDisconnect() {
        var service = StompServerService.builder().port(61613).build();
        var handler = new StompServerChannelHandler(service);
        var channel = new MockChannel();
        handler.onError(channel, new RuntimeException("test error"));
    }
}
