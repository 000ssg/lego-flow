package ssg.legoflow.network.modbus.server;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
/**
 * Service-based Modbus server adapter for composition within the service framework.
 * Wraps ModbusServer to enable Modbus TCP operations through DP/DF pipeline.
 */
public final class ModbusServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private volatile ModbusServer server;
    private volatile Consumer<ModbusResult> requestCallback;

    /** Result of a Modbus request operation. */
    public record ModbusResult(boolean success, int transactionId) {
        public static ModbusResult ok(int tid) { return new ModbusResult(true, tid); }
        public static ModbusResult error(String msg) { return new ModbusResult(false, -1); }
    }

    ModbusServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "Modbus Server Service", builder.priority, builder.dependencies));
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            var memory = new DeviceMemory();
            server = new ModbusServer(0, memory); // Bind to random port
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Modbus server failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public ModbusServer getServer() { return server; }
    public int getPort() { return server != null ? server.localPort() : -1; }
    public void setRequestCallback(Consumer<ModbusResult> cb) { this.requestCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        if (requestCallback != null) requestCallback.accept(ModbusResult.ok(0));
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new ModbusServerChannelHandler(this); }

    public static class Builder {
        private String name = "modbus-server";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public ModbusServerService build() { return new ModbusServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
