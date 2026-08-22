package ssg.legoflow.network.snmp.server.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.network.snmp.security.UsmEngine;
import ssg.legoflow.network.snmp.server.MibTree;
import ssg.legoflow.network.snmp.server.SnmpAgent;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
/** Service-based SNMP agent adapter for composition within the service framework. */
public final class SnmpAgentService extends AbstractService<ByteBuffer, ByteBuffer> {

    private volatile SnmpAgent agent;
    private volatile Consumer<SnmpResult> trapCallback;

    /** Result of an SNMP operation. */
    public record SnmpResult(boolean success, String description) {
        public static SnmpResult ok(String desc) { return new SnmpResult(true, desc); }
        public static SnmpResult error(String msg) { return new SnmpResult(false, msg); }
    }

    SnmpAgentService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "SNMP Agent Service", builder.priority, builder.dependencies));
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            var mibTree = new MibTree();
            byte[] engineId = "snmp-engine-id".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var usmEngine = new UsmEngine(engineId, 0);
            agent = new SnmpAgent(mibTree, usmEngine);
            agent.start();
        } catch (IOException e) {
            throw new RuntimeException("SNMP agent failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (agent != null) agent.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public SnmpAgent getAgent() { return agent; }
    public void setTrapCallback(Consumer<SnmpResult> cb) { this.trapCallback = cb; }

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
        if (trapCallback != null) trapCallback.accept(SnmpResult.ok("SNMP trap processed"));
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new SnmpAgentChannelHandler(this); }

    public static class Builder {
        private String name = "snmp-agent";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public SnmpAgentService build() { return new SnmpAgentService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
