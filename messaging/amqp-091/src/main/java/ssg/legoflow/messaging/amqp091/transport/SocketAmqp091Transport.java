package ssg.legoflow.messaging.amqp091.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP socket implementation of {@link Amqp091Transport}.
 *
 * <p>Uses a plain {@link Socket} with {@link BufferedInputStream}/{@link BufferedOutputStream}
 * to match the official RabbitMQ Java client's {@code SocketFrameHandler}.
 *
 * <p>Implements DP/DF: the socket layer is the DF; the client
 * {@code Amqp091Client} is the DP that applies protocol semantics.
 *
 * @since 0.2.0
 */
public final class SocketAmqp091Transport implements Amqp091Transport {

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Socket socket;
    private volatile boolean open = false;
    private volatile DataInputStream dataIn;
    private volatile DataOutputStream dataOut;

    public SocketAmqp091Transport(String host, int port,
                                   int connectTimeoutMs, int readTimeoutMs) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public void open() throws IOException {
        if (open) return;

        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs > 0 ? readTimeoutMs : 0);

        dataIn = new DataInputStream(
            new BufferedInputStream(socket.getInputStream(), 16384));
        dataOut = new DataOutputStream(
            new BufferedOutputStream(socket.getOutputStream(), 16384));

        open = true;
    }

    @Override
    public int recv(ByteBuffer buffer) throws IOException {
        if (!open || socket == null || socket.isClosed()) {
            return -1;
        }

        int totalRead = 0;
        while (totalRead < buffer.remaining()) {
            byte[] tmp = new byte[buffer.remaining() - totalRead];
            int n = socket.getInputStream().read(tmp);
            if (n <= 0) break;
            buffer.put(tmp, 0, n);
            totalRead += n;
        }
        return totalRead == 0 ? -1 : totalRead;
    }

    @Override
    public void send(ByteBuffer data) throws IOException {
        if (!open || socket == null || socket.isClosed()) {
            throw new IOException("Transport not open");
        }
        if (!data.hasRemaining()) return;

        byte[] tmp = new byte[data.remaining()];
        int pos = data.position();
        data.get(tmp);
        data.position(pos);
        dataOut.write(tmp);
        dataOut.flush();
    }

    @Override
    public DataInputStream getInputStream() throws IOException {
        ensureOpen();
        return dataIn;
    }

    @Override
    public DataOutputStream getOutputStream() throws IOException {
        ensureOpen();
        return dataOut;
    }

    @Override
    public void close() throws IOException {
        boolean wasOpen = open;
        open = false;
        try {
            if (dataIn != null) dataIn.close();
            if (dataOut != null) dataOut.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } finally {
            dataIn = null;
            dataOut = null;
            socket = null;
        }
    }

    @Override
    public boolean isOpen() {
        return open && socket != null && !socket.isClosed();
    }

    private void ensureOpen() throws IOException {
        if (!open) throw new IOException("Transport not open (call open() first)");
    }
}
