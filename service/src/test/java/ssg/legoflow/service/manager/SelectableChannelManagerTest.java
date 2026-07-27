package ssg.legoflow.service.manager;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.demo.combined.ChannelManagerDemo;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class SelectableChannelManagerTest {

    private SelectableChannelManager manager;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new SelectableChannelManager(ctx);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void testRegisterAndGetChannel() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        assertThat(manager.getChannel(service)).isSameAs(channel);
    }

    @Test
    void testRegisterChannelCreatesPipeline() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        assertThat(manager.getChannelPipeline(service)).isNotNull();
    }

    @Test
    void testUnregisterChannelClosesChannel() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);
        assertThat(channel.isOpen()).isTrue();

        manager.unregisterChannel(service);
        assertThat(channel.isOpen()).isFalse();
        assertThat(manager.getChannel(service)).isNull();
        assertThat(manager.getChannelPipeline(service)).isNull();
    }

    @Test
    void testPipelineHandlerAddition() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var pipeline = manager.getChannelPipeline(service);
        var handler = new ChannelManagerDemo.RecordingHandler();
        pipeline.addLast(handler);

        assertThat(pipeline.getHandlers()).hasSize(1).contains(handler);
    }

    @Test
    void testPipelineFireRead() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var handler = new ChannelManagerDemo.RecordingHandler();
        manager.getChannelPipeline(service).addLast(handler);

        var data = ByteBuffer.wrap("hello".getBytes());
        manager.getChannelPipeline(service).fireRead(channel, data);

        assertThat(handler.getEvents()).hasSize(1);
        assertThat(handler.getEvents().getFirst()).isEqualTo("READ:hello");
    }

    @Test
    void testPipelineFireConnect() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var handler = new ChannelManagerDemo.RecordingHandler();
        manager.getChannelPipeline(service).addLast(handler);

        manager.getChannelPipeline(service).fireConnect(channel);

        assertThat(handler.getEvents()).containsExactly("CONNECT");
    }

    @Test
    void testPipelineFireDisconnect() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var handler = new ChannelManagerDemo.RecordingHandler();
        manager.getChannelPipeline(service).addLast(handler);

        manager.getChannelPipeline(service).fireDisconnect(channel);

        assertThat(handler.getEvents()).containsExactly("DISCONNECT");
    }

    @Test
    void testPipelineFireError() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var handler = new ChannelManagerDemo.RecordingHandler();
        manager.getChannelPipeline(service).addLast(handler);

        manager.getChannelPipeline(service).fireError(channel, new RuntimeException("test error"));

        assertThat(handler.getEvents()).containsExactly("ERROR:test error");
    }

    @Test
    void testStartAndStopEventLoop() {
        assertThat(manager.isEventLoopRunning()).isFalse();

        manager.startEventLoop();
        assertThat(manager.isEventLoopRunning()).isTrue();

        manager.stopEventLoop();
        assertThat(manager.isEventLoopRunning()).isFalse();
    }

    @Test
    void testStartAllRegistersAndConnectsServices() {
        var service = new EchoService();
        manager.register(service);
        manager.startAll();

        assertThat(service.isConnected()).isTrue();
        assertThat(service.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testCloseStopsEventLoopAndCleansUp() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);
        manager.startEventLoop();

        manager.close();

        assertThat(manager.isEventLoopRunning()).isFalse();
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void testMultipleHandlersInPipeline() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var handler1 = new ChannelManagerDemo.RecordingHandler();
        var handler2 = new ChannelManagerDemo.RecordingHandler();
        var pipeline = manager.getChannelPipeline(service);
        pipeline.addLast(handler1);
        pipeline.addLast(handler2);

        pipeline.fireConnect(channel);

        assertThat(handler1.getEvents()).containsExactly("CONNECT");
        assertThat(handler2.getEvents()).containsExactly("CONNECT");
    }

    @Test
    void testPipelineAddFirstOrdering() {
        var service = new EchoService();
        manager.register(service);

        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        manager.registerChannel(service, channel);

        var events = new CopyOnWriteArrayList<String>();

        ChannelHandler first = new ChannelManagerDemo.RecordingHandler() {
            @Override
            public void onConnect(DataChannel ch) {
                events.add("FIRST");
                super.onConnect(ch);
            }
        };
        ChannelHandler second = new ChannelManagerDemo.RecordingHandler() {
            @Override
            public void onConnect(DataChannel ch) {
                events.add("SECOND");
                super.onConnect(ch);
            }
        };

        var pipeline = manager.getChannelPipeline(service);
        pipeline.addLast(second);
        pipeline.addFirst(first);

        pipeline.fireConnect(channel);

        assertThat(events).containsExactly("FIRST", "SECOND");
    }

    @Test
    void testInMemoryChannelReadWrite() throws Exception {
        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        channel.putData("test-data".getBytes());

        var readBuf = ByteBuffer.allocate(64);
        int bytesRead = channel.read(readBuf);

        assertThat(bytesRead).isEqualTo(9);
        readBuf.flip();
        var bytes = new byte[readBuf.remaining()];
        readBuf.get(bytes);
        assertThat(new String(bytes)).isEqualTo("test-data");
    }

    @Test
    void testInMemoryChannelClosePreventsFurtherOps() {
        var channel = new ChannelManagerDemo.InMemoryDataChannel();
        channel.close();

        assertThat(channel.isOpen()).isFalse();
        assertThatThrownBy(() -> channel.read(ByteBuffer.allocate(10)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("closed");
    }
}
