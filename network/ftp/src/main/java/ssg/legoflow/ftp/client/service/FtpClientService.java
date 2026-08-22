package ssg.legoflow.ftp.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based FTP client adapter for DP/DF composition. */
public final class FtpClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.ftp.client.FtpClient client;
    private volatile Consumer<FtpResult> responseCallback;

    public record FtpResult(boolean success, String operation, ByteBuffer payload) {
        public static FtpResult ok(String op, ByteBuffer data) { return new FtpResult(true, op, data); }
        public static FtpResult error(String msg) { return new FtpResult(false, null, null); }
    }

    FtpClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "FTP Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            this.client = new ssg.legoflow.ftp.client.FtpClient();
            client.connect(host, port);
        } catch (Exception e) {
            throw new RuntimeException("FTP client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) { try { client.disconnect(); } catch (Exception ignored) {} }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.ftp.client.FtpClient getClient() { return client; }
    public void setResponseCallback(Consumer<FtpResult> cb) { this.responseCallback = cb; }

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
        if (responseCallback != null) responseCallback.accept(FtpResult.ok("ftp", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new FtpClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "ftp-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public FtpClientService build() { return new FtpClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
