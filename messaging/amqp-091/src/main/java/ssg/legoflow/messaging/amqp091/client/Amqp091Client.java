package ssg.legoflow.messaging.amqp091.client;

import ssg.legoflow.messaging.amqp091.common.Amqp091Constants;
import ssg.legoflow.messaging.amqp091.transport.Amqp091Frame;
import ssg.legoflow.messaging.amqp091.transport.Amqp091Transport;
import ssg.legoflow.messaging.amqp091.transport.SocketAmqp091Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AMQP 0-9-1 client using DP/DF (DataProcessor / DataFilter) architecture.
 * Transport layer is abstracted via {@link Amqp091Transport}.
 *
 * <p>Wire format per AMQP 0-9-1 specification:
 * <pre>
 *  Non-heartbeat: TYPE(1) + CHAN(2) + SIZE(4) + PAYLOAD(N) + END(1)
 *  Heartbeat:     TYPE(1) + END(1)
 * </pre>
 *
 * @since 0.2.0
 */
public final class Amqp091Client implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Amqp091Client.class);
    private static final byte[] GREETING = new byte[]{
        0x41, 0x4D, 0x51, 0x50, 0x00, 0x00, 0x09, 0x01  // matches Java client format
    };

    private final Amqp091Transport transport;
    private final ClientConfig config;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger nextChannelId = new AtomicInteger(1);
    private DataInputStream rawIn;
    private DataOutputStream rawOut;
    private volatile int frameMax;
    private volatile int heartbeat;
    private volatile int currentChannel;

    private Amqp091Client(Builder builder) {
        this.config = Objects.requireNonNull(builder.config);
        this.transport = Objects.requireNonNull(builder.transport);
        this.frameMax = builder.config.frameMax();
        this.currentChannel = 0;
    }

    public static Amqp091Client fromConfig(ClientConfig config) {
        return builder()
                .transport(new SocketAmqp091Transport(config.host(), config.port(), 10000, 0))
                .config(config).build();
    }

    // ── Connection lifecycle ──

    public void connect() throws IOException {
        if (connected.get()) return;
        transport.open();
        DataInputStream in = transport.getInputStream();
        DataOutputStream out = transport.getOutputStream();
        LOG.info("AMQP 0-9-1 client connecting to {}:{}", config.host(), config.port());

        out.write(GREETING);
        out.flush();

        // RabbitMQ 4.x sends the connection.start frame directly after the greeting
        // without sending its own greeting first. Standard AMQP 0-9-1 servers may
        // send a greeting response first. We handle both cases.
        byte[] header = new byte[7];
        in.readFully(header);
        byte frameType = header[0];

        // Check if the server sent a greeting response (starts with "AMQP")
        boolean isGreeting = (header[0] == 0x41 && header[1] == 0x4D && header[2] == 0x51 && header[3] == 0x50);

        Amqp091Frame startFrame;
        if (isGreeting) {
            // Server sent its greeting: read remaining 4 bytes
            byte[] greetingRemainder = new byte[4];
            in.readFully(greetingRemainder);
            LOG.info("Server greeting: {} (version {}.{})",
                    new String(header, StandardCharsets.US_ASCII),
                    greetingRemainder[1] & 0xFF, greetingRemainder[2] & 0xFF);
            startFrame = readMethodFrame(in);
        } else {
            // Server sent method frame directly (RabbitMQ 4.x optimization)
            LOG.debug("Server sent method frame directly (type=0x{}), greeting response omitted",
                    Integer.toHexString(frameType & 0xFF));
            int payloadSize = ((header[3] & 0xFF) << 24) | ((header[4] & 0xFF) << 16) |
                              ((header[5] & 0xFF) << 8) | (header[6] & 0xFF);
            byte[] payload = new byte[payloadSize];
            in.readFully(payload);
            byte end = in.readByte();
            if (end != Amqp091Constants.FRAME_END) {
                throw new IOException("Missing frame end octet (0xCE)");
            }
            startFrame = Amqp091Frame.builder()
                    .type(frameType).payloadSize(payloadSize)
                    .channel(((header[1] & 0xFF) << 8) | (header[2] & 0xFF))
                    .payload(payload).build();
        }

        parseConnectionStart(startFrame.payload());
        LOG.info("connection.start received");

        sendStartOk(out);

        Amqp091Frame tuneFrame = readMethodFrame(in);
        ByteBuffer tunePayload = tuneFrame.payload();
        byte[] tuneRaw = new byte[tunePayload.remaining()];
        tunePayload.get(tuneRaw);
        StringBuilder sb = new StringBuilder("Tune hex:");
        for (byte b : tuneRaw) { sb.append(" ").append(String.format("%02X", b & 0xFF)); }
        LOG.info(sb.toString());
        // Tune payload: [channel_max:2][frame_max:4][heartbeat:2] but may include method-id
        // For 12-byte payload, first 4 bytes might be class_id + method_id
        int offset = (tuneRaw.length == 12) ? 4 : 0;
        ByteBuffer tBuf = ByteBuffer.wrap(tuneRaw, offset, tuneRaw.length - offset);
        if (tBuf.remaining() >= 8) {
            tBuf.getShort(); // skip channel_max
            frameMax = tBuf.getInt();
            heartbeat = tBuf.getShort() & 0xFFFF;
        } else {
            // Wrong size - try without skip
            tBuf.getShort();
            frameMax = tBuf.getInt();
            heartbeat = tBuf.getShort() & 0xFFFF;
        }
        LOG.info("Tuned: frameMax={}, heartbeat={}", frameMax, heartbeat);

        sendTuneOk(out);
        sendConnectionOpen(out);

        Amqp091Frame openOkFrame = readMethodFrame(in);
        if (openOkFrame == null || !openOkFrame.isMethod()) {
            throw new IOException("Expected connection.open-ok");
        }

        connected.set(true);
        this.rawIn = in;
        this.rawOut = out;
        LOG.info("AMQP 0-9-1 client connected: {}", config.containerId());
    }

    @Override
    public void close() throws IOException {
        gracefulClose(true);
    }

    public void close(boolean expectCloseOk) throws IOException {
        gracefulClose(expectCloseOk);
    }

    private void gracefulClose(boolean expectCloseOk) throws IOException {
        if (!connected.get() || closed.get()) {
            connected.set(false);
            closed.set(true);
            try { if (transport != null) transport.close(); }
            catch (IOException e) { LOG.warn("Transport close failed", e); }
            return;
        }
        closed.set(true);
        connected.set(false);
        try {
            sendClose(200, "Normal shutdown");
            if (expectCloseOk) {
                Amqp091Frame ok = readFrame();
                if (ok != null && ok.isMethod()) {
                    int methodId = readUnsignedShort(ok.payload());
                    if (methodId != Amqp091Constants.CONNECTION_CLOSE_OK) {
                        LOG.warn("Expected connection.close-ok, got method {}", methodId);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Close protocol failed, closing transport", e);
        } finally {
            try { if (transport != null) transport.close(); }
            catch (IOException e) { LOG.warn("Transport close failed", e); }
        }
    }

    public boolean isConnected() { return connected.get(); }
    public int getCurrentChannel() { return currentChannel; }

    // ── Channel management ──

    public int openChannel() throws IOException {
        int channel = nextChannelId.getAndIncrement();
        sendMethodFrame(channel, Amqp091Constants.CHANNEL_OPEN, new byte[0], 0);
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) {
            throw new IOException("Expected channel.open-ok");
        }
        currentChannel = channel;
        LOG.debug("Opened channel {}", channel);
        return channel;
    }

    public void closeChannel(int channel) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, "OK".getBytes(StandardCharsets.UTF_8));
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.CHANNEL_CLOSE,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readFrame();
        if (ok != null && ok.isMethod()) {
            int closeMethod = readUnsignedShort(ok.payload());
            if (closeMethod != Amqp091Constants.CHANNEL_CLOSE_OK) {
                LOG.warn("Unexpected response to channel.close: method {}", closeMethod);
            }
        }
    }

    // ── Exchange operations ──

    public void declareExchange(String name, String type) throws IOException {
        declareExchange(currentChannel, name, type, false, false, false, false, null);
    }

    private void declareExchange(int channel, String name, String type,
            boolean durable, boolean internal, boolean autoDelete, boolean nowait,
            Map<String, Object> args) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, name.getBytes(StandardCharsets.US_ASCII));
        writeLongString(payload, type.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) (durable ? 1 : 0));
        payload.put((byte) (internal ? 1 : 0));
        payload.put((byte) (autoDelete ? 1 : 0));
        payload.put((byte) (nowait ? 1 : 0));
        if (args != null) {
            byte[] tb = encodeTable(args);
            payload.putInt(tb.length); payload.put(tb);
        } else {
            payload.putInt(0);
        }
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.EXCHANGE_DECLARE,
                payload.array(), payload.remaining());
        if (!nowait) {
            Amqp091Frame ok = readMethodFrame();
            if (ok == null || !ok.isMethod()) throw new IOException("Expected exchange.declare-ok");
        }
    }

    public void deleteExchange(String name) throws IOException {
        deleteExchange(currentChannel, name, false, false);
    }

    private void deleteExchange(int channel, String name, boolean ifUnused, boolean nowait) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, name.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) (ifUnused ? 1 : 0));
        payload.put((byte) (nowait ? 1 : 0));
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.EXCHANGE_DELETE,
                payload.array(), payload.remaining());
        if (!nowait) {
            Amqp091Frame ok = readMethodFrame();
            if (ok == null || !ok.isMethod()) throw new IOException("Expected exchange.delete-ok");
        }
    }

    // ── Queue operations ──

    public QueueDeclareResult declareQueue(String name) throws IOException {
        return declareQueue(currentChannel, name, false, false, true, null);
    }

    public QueueDeclareResult declareQueue(String name, boolean durable,
            boolean exclusive, boolean autoDelete, Map<String, Object> args) throws IOException {
        return declareQueue(currentChannel, name, durable, exclusive, autoDelete, args);
    }

    private QueueDeclareResult declareQueue(int channel, String name, boolean durable,
            boolean exclusive, boolean autoDelete, Map<String, Object> args) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, name.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) (durable ? 1 : 0));
        payload.put((byte) (exclusive ? 1 : 0));
        payload.put((byte) (autoDelete ? 1 : 0));
        payload.put((byte) 0); // reserved
        if (args != null) {
            byte[] tb = encodeTable(args);
            payload.putInt(tb.length); payload.put(tb);
        } else { payload.putInt(0); }
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.QUEUE_DECLARE,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected queue.declare-ok");
        ByteBuffer okPayload = ok.payload();
        okPayload.getShort(); // reserved
        int queueLen = readUnsignedInt(okPayload);
        byte[] queueName = new byte[queueLen];
        okPayload.get(queueName);
        int msgCount = readUnsignedInt(okPayload);
        return new QueueDeclareResult(new String(queueName, StandardCharsets.US_ASCII), msgCount);
    }

    public void queueBind(String queue, String exchange, String routingKey) throws IOException {
        queueBind(currentChannel, queue, exchange, routingKey, null);
    }

    private void queueBind(int channel, String queue, String exchange,
            String routingKey, Map<String, Object> args) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(512);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        writeLongString(payload, exchange.getBytes(StandardCharsets.US_ASCII));
        writeLongString(payload, routingKey.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) 0); // nowait
        if (args != null) {
            byte[] tb = encodeTable(args);
            payload.putInt(tb.length); payload.put(tb);
        } else { payload.putInt(0); }
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.QUEUE_BIND,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected queue.bind-ok");
    }

    public void queueUnbind(String queue, String exchange, String routingKey) throws IOException {
        queueUnbind(currentChannel, queue, exchange, routingKey, null);
    }

    private void queueUnbind(int channel, String queue, String exchange,
            String routingKey, Map<String, Object> args) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(512);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        writeLongString(payload, exchange.getBytes(StandardCharsets.US_ASCII));
        writeLongString(payload, routingKey.getBytes(StandardCharsets.US_ASCII));
        if (args != null) {
            byte[] tb = encodeTable(args);
            payload.putInt(tb.length); payload.put(tb);
        } else { payload.putInt(0); }
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.QUEUE_UNBIND,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected queue.unbind-ok");
    }

    public int queuePurge(String queue) throws IOException {
        return queuePurge(currentChannel, queue);
    }

    private int queuePurge(int channel, String queue) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) 0); // nowait
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.QUEUE_PURGE,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected queue.purge-ok");
        return readUnsignedInt(ok.payload());
    }

    public int queueDelete(String queue) throws IOException {
        return queueDelete(currentChannel, queue, false, false);
    }

    private int queueDelete(int channel, String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) (ifUnused ? 1 : 0));
        payload.put((byte) (ifEmpty ? 1 : 0));
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.QUEUE_DELETE,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected queue.delete-ok");
        ok.payload().getShort(); // reserved
        return readUnsignedInt(ok.payload());
    }

    // ── Basic operations ──

    public void basicQos(int prefetchCount) throws IOException {
        basicQos(currentChannel, prefetchCount, false);
    }

    private void basicQos(int channel, int prefetchCount, boolean global) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(16);
        payload.putInt(prefetchCount);
        payload.put((byte) (global ? 1 : 0));
        payload.put((byte) 0); // reserved
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.BASIC_QOS,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected basic.qos-ok");
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag) throws IOException {
        return basicConsume(currentChannel, queue, autoAck, false, false, consumerTag, null);
    }

    private String basicConsume(int channel, String queue, boolean autoAck,
            boolean noLocal, boolean exclusive, String consumerTag,
            Map<String, Object> args) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        byte[] tagBytes = (consumerTag != null && !consumerTag.isEmpty())
                ? consumerTag.getBytes(StandardCharsets.UTF_8) : new byte[0];
        writeShortString(payload, tagBytes);
        payload.put((byte) (noLocal ? 1 : 0));
        payload.put((byte) (autoAck ? 1 : 0));
        payload.put((byte) (exclusive ? 1 : 0));
        payload.put((byte) 0); // nowait
        if (args != null) {
            byte[] tb = encodeTable(args);
            payload.putInt(tb.length); payload.put(tb);
        } else { payload.putInt(0); }
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.BASIC_CONSUME,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok == null || !ok.isMethod()) throw new IOException("Expected basic.consume-ok");
        byte[] tag = new byte[readUnsignedShort(ok.payload())];
        ok.payload().get(tag);
        return new String(tag, StandardCharsets.UTF_8);
    }

    public void basicCancel(String consumerTag) throws IOException {
        basicCancel(currentChannel, consumerTag, false);
    }

    private void basicCancel(int channel, String consumerTag, boolean nowait) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeShortString(payload, consumerTag.getBytes(StandardCharsets.UTF_8));
        payload.put((byte) (nowait ? 1 : 0));
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.BASIC_CANCEL,
                payload.array(), payload.remaining());
        if (!nowait) {
            Amqp091Frame ok = readMethodFrame();
            if (ok == null || !ok.isMethod()) throw new IOException("Expected basic.cancel-ok");
        }
    }

    public void publish(String routingKey, byte[] body) throws IOException {
        publish(currentChannel, routingKey, body, null, false);
    }

    public void publish(String routingKey, Map<String, Object> headers, byte[] body) throws IOException {
        publish(currentChannel, routingKey, body, headers, false);
    }

    public void publishPersistent(String routingKey, byte[] body) throws IOException {
        publish(currentChannel, routingKey, body, null, true);
    }

    private void publish(int channel, String routingKey, byte[] body,
            Map<String, Object> headers, boolean persistent) throws IOException {
        ByteBuffer methodPayload = ByteBuffer.allocate(256);
        writeLongString(methodPayload, routingKey.getBytes(StandardCharsets.US_ASCII));
        methodPayload.put((byte) 0); // mandatory
        methodPayload.put((byte) 0); // immediate
        methodPayload.flip();
        sendMethodFrame(channel, Amqp091Constants.BASIC_PUBLISH,
                methodPayload.array(), methodPayload.remaining());
        sendContentHeader(channel, body.length, headers, persistent);
        sendContentBody(channel, body);
        LOG.debug("Published {} bytes on channel {} to routing key {}",
                body.length, channel, routingKey);
    }

    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(16);
        payload.putLong(deliveryTag);
        payload.put((byte) (multiple ? 1 : 0));
        payload.flip();
        sendMethodFrame(currentChannel, Amqp091Constants.BASIC_ACK,
                payload.array(), payload.remaining());
    }

    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(16);
        payload.putLong(deliveryTag);
        payload.put((byte) (requeue ? 1 : 0));
        payload.flip();
        sendMethodFrame(currentChannel, Amqp091Constants.BASIC_REJECT,
                payload.array(), payload.remaining());
    }

    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(16);
        payload.putLong(deliveryTag);
        payload.put((byte) (multiple ? 1 : 0));
        payload.put((byte) (requeue ? 1 : 0));
        payload.flip();
        sendMethodFrame(currentChannel, Amqp091Constants.BASIC_NACK,
                payload.array(), payload.remaining());
    }

    public void basicRecover(boolean requeue) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(2);
        payload.put((byte) (requeue ? 1 : 0));
        payload.put((byte) 0); // nowait
        payload.flip();
        sendMethodFrame(currentChannel, Amqp091Constants.BASIC_RECOVER,
                payload.array(), payload.remaining());
        Amqp091Frame ok = readMethodFrame();
        if (ok != null && ok.isMethod()) {
            int methodId = readUnsignedShort(ok.payload());
            if (methodId != Amqp091Constants.BASIC_RECOVER_OK) {
                LOG.warn("Expected basic.recover-ok, got method {}", methodId);
            }
        }
    }

    // ── Message retrieval ──

    public DeliverResult basicGet(String queue) throws IOException {
        return basicGet(currentChannel, queue, true);
    }

    private DeliverResult basicGet(int channel, String queue, boolean autoAck) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        writeLongString(payload, queue.getBytes(StandardCharsets.US_ASCII));
        payload.put((byte) (autoAck ? 1 : 0));
        payload.put((byte) 0); // noselect
        payload.put((byte) 0); // noack
        payload.flip();
        sendMethodFrame(channel, Amqp091Constants.BASIC_GET,
                payload.array(), payload.remaining());

        Amqp091Frame frame = readFrame();
        if (frame == null || !frame.isMethod()) return DeliverResult.empty();

        int methodId = readUnsignedShort(frame.payload());
        if (methodId == Amqp091Constants.BASIC_GET_EMPTY) return DeliverResult.empty();
        if (methodId != Amqp091Constants.BASIC_GET_OK) return DeliverResult.empty();

        long deliveryTag = readUnsignedLong(frame.payload());
        short exchangeLen = frame.payload().getShort();
        byte[] exchange = exchangeLen > 0 ? new byte[exchangeLen] : new byte[0];
        frame.payload().get(exchange);
        short rkLen = frame.payload().getShort();
        byte[] routingKey = rkLen > 0 ? new byte[rkLen] : new byte[0];
        frame.payload().get(routingKey);
        int bitField = frame.payload().get() & 0xFF;
        boolean redelivered = (bitField & 0x01) != 0;

        Amqp091Frame headerFrame = readFrame();
        if (headerFrame == null || headerFrame.type() != Amqp091Constants.FRAME_HEADER) {
            return DeliverResult.empty();
        }
        int bodySize = (int) readUnsignedLong(headerFrame.payload());
        byte[] body = new byte[bodySize];
        if (bodySize > 0) rawIn.readFully(body);
        return DeliverResult.success(routingKey, body);
    }

    public Amqp091Frame deliverNow() throws IOException {
        return readFrame();
    }

    // ── Frame I/O ──

    private void sendMethodFrame(int channel, int methodNumber, byte[] payload, int payloadLen,
                                  DataOutputStream out) throws IOException {
        int classId = methodNumber >> 16;
        int methodId = methodNumber & 0xFFFF;
        int payloadSize = 4 + payloadLen; // class-id(2) + method-id(2) + args
        ByteBuffer buf = ByteBuffer.allocate(1 + 2 + 4 + payloadSize + 1); // type + chan + size + payload + end
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(Amqp091Constants.FRAME_METHOD);
        buf.putShort((short) channel);
        buf.putInt(payloadSize);
        buf.putShort((short) classId);
        buf.putShort((short) methodId);
        buf.put(payload, 0, payloadLen);
        buf.put((byte) Amqp091Constants.FRAME_END);
        buf.flip();
        out.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        out.flush();
    }

    private void sendMethodFrame(int channel, int methodNumber, byte[] payload, int payloadLen) throws IOException {
        sendMethodFrame(channel, methodNumber, payload, payloadLen, rawOut);
    }

    private void writeFully(ByteBuffer buf) throws IOException {
        rawOut.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        rawOut.flush();
    }

    private void sendContentHeader(int channel, int bodySize,
            Map<String, Object> headers, boolean persistent) throws IOException {
        byte[] props = encodeContentProperties(headers, persistent);
        int headerPayloadSize = 2 + 2 + 8 + props.length; // class-id(2) + weight(2) + body-size(8) + props
        ByteBuffer buf = ByteBuffer.allocate(1 + 2 + 4 + headerPayloadSize + 1);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0x09); // header
        buf.putShort((short) channel);
        buf.putInt(headerPayloadSize);
        buf.putShort((short) 60); // Basic class-id
        buf.putShort((short) 0); // weight
        buf.putLong(bodySize);
        buf.put(props);
        buf.put((byte) Amqp091Constants.FRAME_END);
        buf.flip();
        writeFully(buf);
    }

    private void sendContentBody(int channel, byte[] body) throws IOException {
        if (body.length == 0) return;
        int remaining = body.length, offset = 0;
        int chunkSize = frameMax > 0 ? Math.min(frameMax - 8, 65536) : 65536;
        while (remaining > 0) {
            int size = Math.min(remaining, chunkSize);
            ByteBuffer buf = ByteBuffer.allocate(1 + 2 + 4 + size + 1);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 0x0A); // body
            buf.putShort((short) channel);
            buf.putInt(size);
            buf.put(body, offset, size);
            buf.put((byte) Amqp091Constants.FRAME_END);
            buf.flip();
            writeFully(buf);
            remaining -= size;
            offset += size;
        }
    }

    private Amqp091Frame readFrame() throws IOException {
        byte[] header = new byte[7]; // type(1) + chan(2) + size(4)
        rawIn.readFully(header);
        int frameType = header[0] & 0xFF;
        // Heartbeat: type=method, size=0, then frame-end
        // Use size field to detect heartbeat (not available() which may be unreliable after close)
        int size = ((header[3] & 0xFF) << 24) | ((header[4] & 0xFF) << 16) |
                   ((header[5] & 0xFF) << 8) | (header[6] & 0xFF);
        if (frameType == Amqp091Constants.FRAME_HEARTBEAT || size == 0) {
            byte end = rawIn.readByte();
            if (end != Amqp091Constants.FRAME_END) {
                throw new IOException("Missing frame end octet (0xCE) in heartbeat");
            }
            return Amqp091Frame.builder().type((byte)frameType).payloadSize(0).build();
        }
        int channel = ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
        int payloadSize = ((header[3] & 0xFF) << 24) | ((header[4] & 0xFF) << 16) |
                          ((header[5] & 0xFF) << 8) | (header[6] & 0xFF);
        byte[] payload = new byte[payloadSize];
        if (payloadSize > 0) rawIn.readFully(payload);
        byte end = rawIn.readByte();
        if (end != Amqp091Constants.FRAME_END) {
            throw new IOException("Missing frame end octet (0xCE)");
        }
        return Amqp091Frame.builder().type((byte)frameType).payloadSize(payloadSize)
                .payload(payload).channel(channel).build();
    }

    private Amqp091Frame readMethodFrame() throws IOException {
        return readMethodFrame(rawIn);
    }

    private Amqp091Frame readMethodFrame(DataInputStream in) throws IOException {
        byte[] header = new byte[7]; // type(1) + chan(2) + size(4)
        in.readFully(header);
        byte frameType = (byte)(header[0] & 0xFF);
        if (frameType != Amqp091Constants.FRAME_METHOD) {
            throw new IOException("Expected method frame, got type 0x" + Integer.toHexString(frameType & 0xFF));
        }
        // Heartbeat: check if size bytes are all zero
        int size = ((header[3] & 0xFF) << 24) | ((header[4] & 0xFF) << 16) |
                   ((header[5] & 0xFF) << 8) | (header[6] & 0xFF);
        if (size == 0) {
            byte end = in.readByte();
            if (end != Amqp091Constants.FRAME_END) {
                throw new IOException("Missing frame end octet (0xCE) in heartbeat");
            }
            return Amqp091Frame.builder().type(frameType).payloadSize(0).build();
        }
        int channel = ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
        byte[] payload = new byte[size];
        if (size > 0) in.readFully(payload);
        byte end = in.readByte();
        if (end != Amqp091Constants.FRAME_END) {
            throw new IOException("Missing frame end octet (0xCE)");
        }
        return Amqp091Frame.builder().type(frameType).payloadSize(size)
                .channel(channel).payload(payload).build();
    }

    private void sendClose(int replyCode, String replyText) throws IOException {
        ByteBuffer payload = ByteBuffer.allocate(256);
        payload.putShort((short) replyCode);
        writeLongString(payload, replyText.getBytes(StandardCharsets.UTF_8));
        payload.flip();
        sendMethodFrame(0, Amqp091Constants.CONNECTION_CLOSE,
                payload.array(), payload.remaining());
    }

    // ── Handshake ──

    private void parseConnectionStart(ByteBuffer payload) {
        // Parse: version(2) + server-properties(table) + mechanisms(longstr) + locales(longstr)
        byte major = payload.get();
        byte minor = payload.get();
        LOG.info("Server version: {}.{}", major & 0xFF, minor & 0xFF);
        
        // server-properties: table (4-byte size + table content)
        int tableLen = readUnsignedInt(payload);
        if (tableLen > 0 && payload.remaining() >= tableLen) {
            payload.position(payload.position() + tableLen);
        }
        
        // mechanisms: longstr (4-byte size + mechanism names)
        int mechLen = readUnsignedInt(payload);
        if (mechLen > 0) {
            LOG.info("Server mechanisms length: {}", mechLen);
            payload.position(payload.position() + mechLen);
        }
        
        // locales: longstr (4-byte size + locale names)
        int localeLen = readUnsignedInt(payload);
        if (localeLen > 0 && payload.remaining() >= localeLen) {
            LOG.info("Server locales length: {}", localeLen);
            payload.position(payload.position() + localeLen);
        }
        LOG.debug("Parsed connection.start at position {}", payload.position());
    }

    private void sendStartOk(DataOutputStream out) throws IOException {
        byte[] clientProps = buildClientProperties();
        byte[] mechanism = "PLAIN".getBytes(StandardCharsets.US_ASCII);
        byte[] response = ("\0" + config.username() + "\0" + config.password()).getBytes(StandardCharsets.UTF_8);
        byte[] locale = "en_US".getBytes(StandardCharsets.UTF_8);

        // AMQP 0-9-1 connection.start-ok: [client_properties:table][mechanism:shortstr][response:longstr][locale:shortstr]
        ByteBuffer payloadBuf = ByteBuffer.allocate(128 + clientProps.length + mechanism.length + response.length + locale.length);
        payloadBuf.put(clientProps);
        payloadBuf.put((byte) mechanism.length); // shortstr for mechanism
        payloadBuf.put(mechanism);
        payloadBuf.putInt(response.length);       // longstr for response
        payloadBuf.put(response);
        payloadBuf.put((byte) locale.length);     // shortstr for locale
        payloadBuf.put(locale);
        payloadBuf.flip();
        sendMethodFrame(0, Amqp091Constants.CONNECTION_START_OK,
                payloadBuf.array(), payloadBuf.remaining(), out);
    }

    private void sendTuneOk(DataOutputStream out) throws IOException {
        // AMQP 0-9-1 tune-ok: [channel_max:2][frame_max:4][heartbeat:2]
        // Use server's channel_max limit since 0 (no limit) is rejected by RabbitMQ 4.x
        int clampedFrameMax = Math.min(frameMax, Amqp091Constants.DEFAULT_MAX_FRAME_SIZE);
        int channelMax = config.channelMax();  // use config value
        ByteBuffer payload = ByteBuffer.allocate(8);
        payload.putShort((short) channelMax);     // channel max
        payload.putInt(clampedFrameMax);          // frame max (clamped to 131072)
        payload.putShort((short) heartbeat);      // heartbeat
        payload.flip();
        LOG.info("Tune-ok sent: channelMax={}, frameMax={}, heartbeat={}", channelMax, clampedFrameMax, heartbeat);
        sendMethodFrame(0, Amqp091Constants.CONNECTION_TUNE_OK,
                payload.array(), payload.remaining(), out);
    }

    private void sendConnectionOpen(DataOutputStream out) throws IOException {
        // AMQP 0-9-1 wire format used by RabbitMQ / Java client:
        // [virtual-host:shortstr][capabilities:shortstr][reserved2:bitfield]
        // Note: Java client skips reserved1 and uses shortstr (not longstr) for vhost/caps.
        // Wire for "/" vhost: 01 2F 00 00  (4 bytes, no reserved1)
        ByteBuffer payload = ByteBuffer.allocate(32);
        byte[] vhost = config.virtualHost().getBytes(StandardCharsets.UTF_8);
        payload.put((byte) vhost.length);   // shortstr length for virtual-host
        payload.put(vhost);                 // virtual-host data
        payload.put((byte) 0);              // capabilities = empty shortstr (length 0)
        payload.put((byte) 0x00);           // reserved2: bitfield (1 bit = false, padded to byte)
        payload.flip();
        sendMethodFrame(0, Amqp091Constants.CONNECTION_OPEN,
                payload.array(), payload.remaining(), out);
    }

    // ── Encoding helpers ──

    private byte[] buildClientProperties() {
        // AMQP 0-9-1 table: [table_size(int32)][entries...]
        ByteBuffer bodyBuf = ByteBuffer.allocate(256);
        writeTableField(bodyBuf, "product", "Lego-Flow-AMQP");
        writeTableField(bodyBuf, "platform", "Java");
        writeTableField(bodyBuf, "version", "0.2.0-SNAPSHOT");
        writeTableField(bodyBuf, "information", "Lego Flow AMQP 0-9-1 client");
        int bodyLen = bodyBuf.position();
        ByteBuffer payload = ByteBuffer.allocate(4 + bodyLen);
        payload.putInt(bodyLen); // 4-byte table size prefix
        bodyBuf.flip();
        payload.put(bodyBuf);
        payload.flip();
        byte[] result = new byte[payload.remaining()];
        payload.get(result);
        return result;
    }

    private void writeTableField(ByteBuffer table, String key, String value) {
        // AMQP 0-9-1 table entry: [key_shortstr][value_encoding]
        // String value: 'S' + longstr (4-byte length + data)
        byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
        table.put((byte) keyBytes.length);
        table.put(keyBytes);
        table.put((byte) 'S'); // string type indicator
        byte[] valBytes = value.getBytes(StandardCharsets.US_ASCII);
        table.putInt(valBytes.length); // 4-byte longstr length
        table.put(valBytes);
    }

    private byte[] encodeTable(Map<String, Object> table) {
        // AMQP 0-9-1 table: [table_size(int32)][entries...]
        ByteBuffer bodyBuf = ByteBuffer.allocate(512);
        for (Map.Entry<String, Object> entry : table.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            bodyBuf.put((byte) key.length); // shortstr key length
            bodyBuf.put(key);
            Object val = entry.getValue();
            if (val instanceof String) {
                bodyBuf.put((byte) 'S'); // string type
                byte[] vb = ((String) val).getBytes(StandardCharsets.US_ASCII);
                bodyBuf.putInt(vb.length); // 4-byte longstr length
                bodyBuf.put(vb);
            } else if (val instanceof Integer) {
                bodyBuf.put((byte) 'I'); // integer type
                bodyBuf.putInt((Integer) val);
            } else if (val instanceof Boolean) {
                bodyBuf.put((byte) 't'); // boolean type
                bodyBuf.put((byte) ((Boolean) val ? 1 : 0));
            } else if (val instanceof byte[]) {
                bodyBuf.put((byte) 'B'); // binary type
                byte[] vb = (byte[]) val;
                bodyBuf.putInt(vb.length);
                bodyBuf.put(vb);
            }
        }
        int bodyLen = bodyBuf.position();
        ByteBuffer buf = ByteBuffer.allocate(4 + bodyLen);
        buf.putInt(bodyLen); // 4-byte table size prefix
        bodyBuf.flip();
        buf.put(bodyBuf);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    private void writeLongString(ByteBuffer buf, byte[] value) {
        buf.putInt(value.length);
        buf.put(value);
    }

    private void writeShortString(ByteBuffer buf, byte[] value) {
        buf.put((byte) value.length);
        buf.put(value);
    }

    private static int readUnsignedInt(ByteBuffer buf) { return buf.getInt() & 0xFFFFFFFF; }
    private static int readUnsignedShort(ByteBuffer buf) { return buf.getShort() & 0xFFFF; }
    private static long readUnsignedLong(ByteBuffer buf) { return buf.getLong() & 0xFFFFFFFFFFFFFFFFL; }

    private byte[] encodeContentProperties(Map<String, Object> headers, boolean persistent) {
        int count = 0;
        if (headers != null) count++;
        count++; // content-type
        if (persistent) count++; // delivery-mode

        byte[] props = new byte[512];
        ByteBuffer buf = ByteBuffer.wrap(props);
        buf.put((byte) count); // property count

        if (headers != null) {
            buf.put((byte) 'h');
            byte[] keyBytes = "headers".getBytes(StandardCharsets.US_ASCII);
            buf.put((byte) keyBytes.length);
            buf.put(keyBytes);
            buf.put((byte) 'T'); // table
            byte[] tableBytes = encodeTable(headers);
            buf.putInt(tableBytes.length);
            buf.put(tableBytes);
        }
        buf.put((byte) 't');
        byte[] ctKey = "content-type".getBytes(StandardCharsets.US_ASCII);
        buf.put((byte) ctKey.length);
        buf.put(ctKey);
        buf.put((byte) 'S');
        byte[] ctVal = "application/octet-stream".getBytes(StandardCharsets.US_ASCII);
        buf.putShort((short) ctVal.length);
        buf.put(ctVal);
        if (persistent) {
            buf.put((byte) 'd');
            byte[] dmKey = "delivery-mode".getBytes(StandardCharsets.US_ASCII);
            buf.put((byte) dmKey.length);
            buf.put(dmKey);
            buf.put((byte) 'I');
            buf.putInt(2);
        }
        int written = props.length - buf.remaining();
        byte[] result = new byte[written];
        System.arraycopy(props, 0, result, 0, written);
        return result;
    }

    // ── Result classes ──

    public static class QueueDeclareResult {
        private final String queueName;
        private final int messageCount;
        public QueueDeclareResult(String q, int m) { queueName = q; messageCount = m; }
        public String queueName() { return queueName; }
        public int messageCount() { return messageCount; }
    }

    public static class DeliverResult {
        private final boolean empty;
        private final byte[] routingKey;
        private final byte[] body;
        private DeliverResult(boolean e, byte[] rk, byte[] b) { empty = e; routingKey = rk; body = b; }
        public static DeliverResult empty() { return new DeliverResult(true, null, null); }
        public static DeliverResult success(byte[] rk, byte[] b) { return new DeliverResult(false, rk, b); }
        public boolean isEmpty() { return empty; }
        public byte[] routingKey() { return routingKey; }
        public byte[] body() { return body; }
    }

    // ── Builder ──

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Amqp091Transport transport;
        private ClientConfig config = ClientConfig.builder().build();
        public Builder transport(Amqp091Transport t) { transport = t; return this; }
        public Builder config(ClientConfig c) { config = c; return this; }
        public Amqp091Client build() { return new Amqp091Client(this); }
    }
}
