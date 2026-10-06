package ssg.legoflow.messaging.stomp.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class StompClientChannelHandlerTest {

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException { return 0; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return null; }
    }

    @Test void testHandlerConstruction() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        assertThat(handler).isNotNull();
    }

    @Test void testConnectLatchCountsDown() throws Exception {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        var latch = new CountDownLatch(1);
        handler.setConnectLatch(latch);

        assertThat(latch.getCount()).isEqualTo(1);
        // onConnect signals protocol readiness regardless of channel type —
        // the TCP lifecycle (finishConnect, interestOps) is the manager's job,
        // so the handler no longer distinguishes TcpDataChannel from other channels.
        handler.onConnect(new MockChannel());
        assertThat(latch.getCount()).isZero();
        // Idempotent: a repeated fire does not re-signal.
        handler.onConnect(new MockChannel());
        assertThat(latch.getCount()).isZero();
    }

    @Test void testOnReadWithNoTransport() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        var channel = new MockChannel();
        handler.onRead(channel, ByteBuffer.wrap("test".getBytes()));
    }

    @Test void testOnWriteWithNoTransport() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        var channel = new MockChannel();
        handler.onWrite(channel);
    }

    @Test void testOnDisconnect() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        var channel = new MockChannel();
        handler.onDisconnect(channel);
    }

    @Test void testOnError() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = new StompClientChannelHandler(service, service.getServiceContext());
        var channel = new MockChannel();
        handler.onError(channel, new RuntimeException("test"));
    }
}
