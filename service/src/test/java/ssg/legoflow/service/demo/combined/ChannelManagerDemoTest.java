package ssg.legoflow.service.demo.combined;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.ServiceState;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.filter.ContextPropagationFilter;
import ssg.legoflow.service.manager.AsyncServicesManager;
import ssg.legoflow.service.manager.ConnectionThread;
import ssg.legoflow.service.manager.ProcessingThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class ChannelManagerDemoTest {

    private ChannelManagerDemo demo;

    @BeforeEach
    void setUp() {
        demo = new ChannelManagerDemo();
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testChannelRegistrationAndPipeline() {
        var service = demo.createAndRegisterEchoService();
        var channel = demo.createInMemoryChannel();
        var handler = demo.createRecordingHandler();

        demo.getManager().registerChannel(service, channel);
        var pipeline = demo.getManager().getChannelPipeline(service);
        pipeline.addLast(handler);

        pipeline.fireConnect(channel);
        pipeline.fireRead(channel, ByteBuffer.wrap("hello-world".getBytes()));
        pipeline.fireDisconnect(channel);

        assertThat(handler.getEvents()).containsExactly(
                "CONNECT",
                "READ:hello-world",
                "DISCONNECT"
        );
    }

    @Test
    void testServiceLifecycleWithChannelManager() {
        var service = demo.createAndRegisterEchoService();
        demo.getManager().startAll();

        assertThat(service.isConnected()).isTrue();
        assertThat(service.getState()).isEqualTo(ProcessorState.READY);

        var channel = demo.createInMemoryChannel();
        demo.getManager().registerChannel(service, channel);

        assertThat(demo.getManager().getChannel(service)).isSameAs(channel);

        demo.getManager().stopAll();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testAsyncServicesManagerWrapper() throws Exception {
        var service = demo.createAndRegisterEchoService();
        var asyncManager = new AsyncServicesManager(demo.getManager());

        asyncManager.startAll().get();
        assertThat(service.isConnected()).isTrue();

        var states = asyncManager.getStates().get();
        assertThat(states).containsEntry("echo", ProcessorState.READY);

        asyncManager.stopAll().get();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testConnectionThreadEstablishesConnection() throws Exception {
        var service = demo.createAndRegisterEchoService();
        var connectionThread = new ConnectionThread(service, demo.getContext());

        var future = connectionThread.start();
        future.get();

        assertThat(service.isConnected()).isTrue();
        assertThat(service.getState()).isEqualTo(ProcessorState.READY);
    }

    @Test
    void testProcessingThreadHandlesRead() throws Exception {
        var service = demo.createAndRegisterEchoService();
        var channel = demo.createInMemoryChannel();
        channel.putData("process-this".getBytes());

        var handler = demo.createRecordingHandler();
        var pipeline = new ChannelPipeline();
        pipeline.addLast(handler);

        var processingThread = new ProcessingThread(channel, pipeline, 4096);
        processingThread.processReadable();

        Thread.sleep(200);

        assertThat(handler.getEvents()).hasSize(1);
        assertThat(handler.getEvents().getFirst()).isEqualTo("READ:process-this");
    }

    @Test
    void testMultipleHandlersChainedInPipeline() {
        var service = demo.createAndRegisterEchoService();
        var channel = demo.createInMemoryChannel();

        demo.getManager().registerChannel(service, channel);
        var pipeline = demo.getManager().getChannelPipeline(service);

        var handler1 = demo.createRecordingHandler();
        var handler2 = demo.createRecordingHandler();
        pipeline.addLast(handler1);
        pipeline.addLast(handler2);

        pipeline.fireRead(channel, ByteBuffer.wrap("data".getBytes()));

        assertThat(handler1.getEvents()).containsExactly("READ:data");
        assertThat(handler2.getEvents()).containsExactly("READ:data");
    }

    @Test
    void testContextPropagationFilter() {
        var sourceCtx = demo.getContext();
        sourceCtx.getRequestScope().setAttribute("trace-id", "abc-123");
        sourceCtx.getSessionScope().setAttribute("user-pref", "dark-mode");

        var filter = new ContextPropagationFilter<>(String.class, sourceCtx);

        var targetCtx = new ssg.legoflow.service.DefaultServiceContext(
                ssg.legoflow.service.user.ServiceUser.anonymous());

        filter.filter(targetCtx, "test-data");

        assertThat(targetCtx.getRequestScope().<String>getAttribute("trace-id")).isEqualTo("abc-123");
        assertThat(targetCtx.getSessionScope().<String>getAttribute("user-pref")).isEqualTo("dark-mode");
    }

    @Test
    void testServiceStateTransitions() {
        assertThat(ServiceState.IDLE.canTransitionTo(ServiceState.CONNECTING_TRANSPORT)).isTrue();
        assertThat(ServiceState.CONNECTING_TRANSPORT.canTransitionTo(ServiceState.AUTHENTICATING)).isTrue();
        assertThat(ServiceState.AUTHENTICATING.canTransitionTo(ServiceState.READY)).isTrue();
        assertThat(ServiceState.READY.canTransitionTo(ServiceState.DRAINING)).isTrue();
        assertThat(ServiceState.DRAINING.canTransitionTo(ServiceState.DISCONNECTING)).isTrue();
        assertThat(ServiceState.DISCONNECTING.canTransitionTo(ServiceState.IDLE)).isTrue();
        assertThat(ServiceState.STOPPED.canTransitionTo(ServiceState.IDLE)).isFalse();
    }

    @Test
    void testServiceStateToProcessorStateMapping() {
        assertThat(ServiceState.IDLE.toProcessorState()).isEqualTo(ProcessorState.IDLE);
        assertThat(ServiceState.CONNECTING_TRANSPORT.toProcessorState()).isEqualTo(ProcessorState.CONNECTING);
        assertThat(ServiceState.AUTHENTICATING.toProcessorState()).isEqualTo(ProcessorState.CONNECTING);
        assertThat(ServiceState.READY.toProcessorState()).isEqualTo(ProcessorState.READY);
        assertThat(ServiceState.PAUSED.toProcessorState()).isEqualTo(ProcessorState.PAUSED);
        assertThat(ServiceState.FAILED.toProcessorState()).isEqualTo(ProcessorState.FAILED);
        assertThat(ServiceState.STOPPED.toProcessorState()).isEqualTo(ProcessorState.STOPPED);
    }

    @Test
    void testEventLoopStartStop() {
        demo.getManager().startEventLoop();
        assertThat(demo.getManager().isEventLoopRunning()).isTrue();

        demo.getManager().stopEventLoop();
        assertThat(demo.getManager().isEventLoopRunning()).isFalse();
    }

    @Test
    void testChannelReadWriteRoundTrip() throws Exception {
        var channel = demo.createInMemoryChannel();
        var message = "round-trip-test";

        channel.putData(message.getBytes());

        var readBuf = ByteBuffer.allocate(256);
        int bytesRead = channel.read(readBuf);
        readBuf.flip();

        assertThat(bytesRead).isEqualTo(message.length());
        var bytes = new byte[readBuf.remaining()];
        readBuf.get(bytes);
        assertThat(new String(bytes)).isEqualTo(message);
    }
}
