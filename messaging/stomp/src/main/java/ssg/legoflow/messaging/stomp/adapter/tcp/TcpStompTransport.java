package ssg.legoflow.messaging.stomp.adapter.tcp;

import ssg.legoflow.messaging.stomp.core.StompCodec;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * STOMP transport over raw TCP sockets.
 *
 * <p>Handles frame boundary detection using the NULL byte ({@code \0}) terminator.
 * Reads from the socket input stream byte by byte, accumulating until a NULL byte
 * is found, then decodes the complete frame.
 *
 * @since 0.1.0
 */
public class TcpStompTransport implements StompTransport {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private volatile boolean open;

    /**
     * Creates a TCP transport wrapping the given socket.
     *
     * @param socket the TCP socket
     * @throws IOException if streams cannot be obtained
     */
    public TcpStompTransport(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.open = true;
    }

    @Override
    public void send(StompFrame frame) {
        if (!open) throw new IllegalStateException("Transport is closed");
        try {
            byte[] data = StompCodec.encode(frame);
            synchronized (out) {
                out.write(data);
                out.flush();
            }
        } catch (IOException e) {
            open = false;
            throw new RuntimeException("Failed to send frame", e);
        }
    }

    @Override
    public StompFrame receive() {
        if (!open) throw new IllegalStateException("Transport is closed");
        try {
            var buffer = new ByteArrayOutputStream(512);
            boolean inBody = false;
            int contentLength = -1;
            int headerEndPos = -1;
            int bodyBytesRead = 0;

            while (true) {
                int b = in.read();
                if (b == -1) {
                    open = false;
                    if (buffer.size() == 0) return null;
                    // Try to parse what we have
                    break;
                }

                buffer.write(b);

                // If we haven't found the header end yet
                if (!inBody) {
                    byte[] current = buffer.toByteArray();
                    // Look for \n\n or \r\n\r\n indicating end of headers
                    int hdrEnd = findHeaderEnd(current);
                    if (hdrEnd >= 0) {
                        inBody = true;
                        headerEndPos = hdrEnd;
                        // Check for content-length in headers
                        contentLength = extractContentLength(current, headerEndPos);
                        bodyBytesRead = current.length - headerEndPos;

                        if (contentLength >= 0 && bodyBytesRead >= contentLength) {
                            // Need to read the NULL terminator
                            int next = in.read();
                            if (next == 0) {
                                // Frame complete
                            } else if (next != -1) {
                                buffer.write(next);
                            }
                            break;
                        } else if (contentLength < 0 && b == 0) {
                            break;
                        }
                    }
                } else {
                    // In body
                    if (contentLength >= 0) {
                        bodyBytesRead = buffer.size() - headerEndPos;
                        if (bodyBytesRead >= contentLength) {
                            // Read the NULL terminator
                            int next = in.read();
                            if (next == 0) {
                                // Frame complete
                            } else if (next != -1) {
                                buffer.write(next);
                            }
                            break;
                        }
                    } else if (b == 0) {
                        // NULL terminator found
                        break;
                    }
                }
            }

            byte[] data = buffer.toByteArray();
            if (data.length == 0) return null;

            // Check for heart-beat (just newlines)
            boolean allNewlines = true;
            for (byte v : data) {
                if (v != '\n' && v != '\r' && v != 0) {
                    allNewlines = false;
                    break;
                }
            }
            if (allNewlines) {
                return StompFrame.heartbeat();
            }

            return StompCodec.decode(data);
        } catch (IOException e) {
            open = false;
            throw new RuntimeException("Failed to receive frame", e);
        }
    }

    @Override
    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    /**
     * Finds the end of headers (position after the blank line separator).
     * Returns -1 if not found.
     */
    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i + 2;
            }
            if (i + 3 < data.length
                    && data[i] == '\r' && data[i + 1] == '\n'
                    && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    /**
     * Extracts content-length from raw header bytes. Returns -1 if not found.
     */
    private int extractContentLength(byte[] data, int headerEnd) {
        String headerSection = new String(data, 0, headerEnd, java.nio.charset.StandardCharsets.UTF_8);
        for (String line : headerSection.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(trimmed.substring("content-length:".length()).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
