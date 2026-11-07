package ssg.legoflow.http.transport;

/**
 * Stream adapter that wraps a {@link java.net.Socket} to provide InputStream/OutputStream
 * for legacy HTTP code that expects stream-based I/O.
 *
 * <p>This adapter is used by the service layer to bridge between socket-based and
 * transport-based HTTP implementations.
 *
 * @since 0.3.0
 */
public final class HttpTransportStreamAdapter implements AutoCloseable {

    private final java.net.Socket socket;
    private final java.io.InputStream inputStream;
    private final java.io.OutputStream outputStream;

    public HttpTransportStreamAdapter(java.net.Socket socket) {
        this.socket = socket;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to get streams from socket", e);
        }
    }

    public java.io.InputStream getInputStream() {
        return inputStream;
    }

    public java.io.OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws java.io.IOException {
        socket.close();
    }
}
