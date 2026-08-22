package ssg.legoflow.email.smtp.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
/** Service-based SMTP client adapter for sending emails through the DP/DF pipeline. */
public final class SmtpClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private final String username;
    private final byte[] password;
    private final boolean useTls;
    
    private volatile SmtpClient client;
    private volatile java.util.function.Consumer<SmtpSendResult> sendCallback;

    /** Result of an SMTP send operation. */
    public record SmtpSendResult(boolean success, String message) {
        public static SmtpSendResult ok(String msg) { return new SmtpSendResult(true, msg); }
        public static SmtpSendResult error(String msg) { return new SmtpSendResult(false, msg); }
    }

    SmtpClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "SMTP Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.useTls = builder.useTls;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            var tlsMode = useTls 
                ? SmtpClientConfig.TlsMode.STARTTLS 
                : SmtpClientConfig.TlsMode.NONE;
            
            var configBuilder = SmtpClientConfig.builder(host, port).tlsMode(tlsMode);
            if (username != null && password != null) {
                configBuilder.auth(username, new String(password, StandardCharsets.UTF_8));
            }
            
            this.client = new SmtpClient(configBuilder.build());
            client.connect();
        } catch (Exception e) {
            throw new RuntimeException("SMTP client connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    public SmtpClient getClient() { return client; }
    public void setSendCallback(java.util.function.Consumer<SmtpSendResult> cb) { this.sendCallback = cb; }

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
        try {
            String message = new String(bytes, StandardCharsets.UTF_8);
            var reply = client.send(
                "default@example.com",
                List.of("recipient@example.com"),
                message
            );
            
            if (sendCallback != null) {
                sendCallback.accept(SmtpSendResult.ok(String.valueOf(reply.code())));
            }
        } catch (Exception e) {
            if (sendCallback != null) {
                sendCallback.accept(SmtpSendResult.error(e.getMessage()));
            }
        }
    }

    private void processOutboundData(ByteBuffer data) {}

    public static class Builder {
        private final String host; private final int port;
        private String name = "smtp-client";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private String username; private byte[] password;
        private boolean useTls = true;

        public Builder(String h, int p) { this.host = h; this.port = p; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder username(String u) { this.username = u; return this; }
        public Builder password(String p) { this.password = p.getBytes(StandardCharsets.UTF_8); return this; }
        public Builder useTls(boolean t) { this.useTls = t; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public SmtpClientService build() { return new SmtpClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
