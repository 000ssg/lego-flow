package ssg.legoflow.database.mysql.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Adapter that wraps a {@link MysqlTransport} as an {@link InputStream} and {@link OutputStream}.
 *
 * <p>Allows existing MySQL protocol code that uses streams to work with the transport SPI.
 * Note: this adapter is blocking and should only be used in tests or single-connection contexts.
 *
 * @since 0.3.0
 */
public final class MysqlTransportStreamAdapter {

    private final MysqlTransport transport;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public MysqlTransportStreamAdapter(MysqlTransport transport) {
        this.transport = transport;
        this.inputStream = new InputStream() {
            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                ByteBuffer bb = ByteBuffer.wrap(buffer, off, len);
                int n = transport.receiveWithTimeout(bb, 5, java.util.concurrent.TimeUnit.SECONDS);
                if (n <= 0) {
                    return -1;
                }
                return n;
            }

            @Override
            public int read() throws IOException {
                ByteBuffer bb = ByteBuffer.allocate(1);
                int n = transport.receiveWithTimeout(bb, 5, java.util.concurrent.TimeUnit.SECONDS);
                if (n <= 0) {
                    return -1;
                }
                bb.flip();
                return bb.get() & 0xFF;
            }
        };
        this.outputStream = new OutputStream() {
            @Override
            public void write(byte[] buffer, int off, int len) throws IOException {
                transport.send(ByteBuffer.wrap(buffer, off, len));
            }

            @Override
            public void write(int b) throws IOException {
                transport.send(ByteBuffer.wrap(new byte[]{(byte) b}));
            }
        };
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public MysqlTransport getTransport() {
        return transport;
    }
}
