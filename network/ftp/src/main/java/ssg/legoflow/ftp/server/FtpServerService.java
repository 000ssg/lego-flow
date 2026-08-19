package ssg.legoflow.ftp.server;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
/** Service-based FTP server adapter for composition within the service framework. */
public final class FtpServerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private volatile FtpServer server;
    private volatile Consumer<FtpResult> commandCallback;

    /** Result of an FTP operation. */
    public record FtpResult(boolean success, String description) {
        public static FtpResult ok(String desc) { return new FtpResult(true, desc); }
        public static FtpResult error(String msg) { return new FtpResult(false, msg); }
    }

    FtpServerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "FTP Server Service", builder.priority, builder.dependencies));
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            server = new FtpServer(FtpServerConfig.defaults());
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("FTP server failed to start", e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public FtpServer getServer() { return server; }
    public void setCommandCallback(Consumer<FtpResult> cb) { this.commandCallback = cb; }

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
        if (commandCallback != null) commandCallback.accept(FtpResult.ok("FTP command processed"));
    }

    private void processOutboundData(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new FtpServerChannelHandler(this); }

    public static class Builder {
        private String name = "ftp-server";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public FtpServerService build() { return new FtpServerService(this); }
    }

    public static Builder builder() { return new Builder(); }
}
