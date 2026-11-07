package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Generic stream adapter that wraps a {@link Socket} to provide
 * {@link InputStream}/{@link OutputStream} for legacy code.
 *
 * <p>This eliminates the need for protocol-specific stream adapters.
 *
 * @since 0.3.0
 */
public final class TransportStreamAdapter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TransportStreamAdapter.class);

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Creates a new transport stream adapter.
     *
     * @param socket the underlying socket
     * @throws IOException if the socket streams cannot be obtained
     */
    public TransportStreamAdapter(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    /**
     * Returns the underlying socket.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Returns the input stream.
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Returns the output stream.
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Returns a readable byte channel backed by the input stream.
     */
    public ReadableByteChannel readableChannel() {
        return Channels.newChannel(inputStream);
    }

    /**
     * Returns a writable byte channel backed by the output stream.
     */
    public WritableByteChannel writableChannel() {
        return Channels.newChannel(outputStream);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
