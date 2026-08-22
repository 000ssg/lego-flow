package ssg.legoflow.messaging.amqp091.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP socket implementation of {@link Amqp091Transport}.
 *
 * <p>Creates the socket, applies TCP options, and wraps I/O with
 * {@link BufferedInputStream}/{@link BufferedOutputStream} to match
 * the official RabbitMQ Java client's {@code SocketFrameHandler}.
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
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private final AtomicBoolean open = new AtomicBoolean(false);

    public SocketAmqp091Transport(String host, int port,
                                   int connectTimeoutMs, int readTimeoutMs) {
        this.host = Objects.requireNonNull(host, "host required");
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public void open() throws IOException {
        if (open.get()) return;

        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);

        // Wrap with buffering to match official RabbitMQ client SocketFrameHandler
        InputStream rawIn  = new BufferedInputStream(socket.getInputStream(),  16384);
        OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream(), 16384);

        dataIn  = new DataInputStream(rawIn);
        dataOut = new DataOutputStream(rawOut);
        open.set(true);
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
    public int recv(ByteBuffer buffer) throws IOException {
        if (!open.get()) { buffer.clear(); return -1; }

        int totalRead = 0;
        while (totalRead < buffer.remaining()) {
            int n;
            if (buffer.hasArray()) {
                int pos = buffer.arrayOffset() + buffer.position();
                int space = buffer.remaining();
                n = dataIn.read(buffer.array(), pos, space);
            } else {
                byte[] tmp = new byte[Math.min(buffer.remaining(), 8192)];
                n = dataIn.read(tmp, 0, tmp.length);
                if (n > 0) buffer.put(tmp, 0, n);
            }
            if (n <= 0) break;
            totalRead += n;
            buffer.position(buffer.position() + n);
        }
        return totalRead == 0 ? -1 : totalRead;
    }

    @Override
    public void send(ByteBuffer data) throws IOException {
        if (!open.get()) throw new IOException("Transport not open");
        if (!data.hasRemaining()) return;

        if (data.hasArray()) {
            dataOut.write(data.array(), data.arrayOffset() + data.position(), data.remaining());
        } else {
            int pos = data.position();
            byte[] tmp = new byte[data.remaining()];
            data.get(tmp);
            data.position(pos);
            dataOut.write(tmp);
        }
        dataOut.flush();
    }

    @Override
    public void close() throws IOException {
        open.set(false);
        try {
            if (dataIn  != null) dataIn.close();
            if (dataOut != null) dataOut.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } finally {
            dataIn = null; dataOut = null; socket = null;
        }
    }

    @Override
    public boolean isOpen() {
        return open.get() && socket != null && !socket.isClosed();
    }

    private void ensureOpen() throws IOException {
        if (!open.get()) throw new IOException("Transport not open (call open() first)");
    }
}
