package ssg.legoflow.network.modbus.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based Modbus client adapter for DP/DF composition. */
public final class ModbusClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.network.modbus.client.ModbusClient client;
    private volatile Consumer<ModbusResult> responseCallback;

    public record ModbusResult(boolean success, int transactionId, ByteBuffer payload) {
        public static ModbusResult ok(int tid, ByteBuffer data) { return new ModbusResult(true, tid, data); }
        public static ModbusResult error(String msg) { return new ModbusResult(false, -1, null); }
    }

    ModbusClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "Modbus Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.client = new ssg.legoflow.network.modbus.client.ModbusClient(host, port);
        } catch (Exception e) {
            throw new RuntimeException("Modbus client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.close(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.network.modbus.client.ModbusClient getClient() { return client; }
    public void setResponseCallback(Consumer<ModbusResult> cb) { this.responseCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInbound(ByteBuffer data) {
        if (responseCallback != null) responseCallback.accept(ModbusResult.ok(0, data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new ModbusClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "modbus-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public ModbusClientService build() { return new ModbusClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
