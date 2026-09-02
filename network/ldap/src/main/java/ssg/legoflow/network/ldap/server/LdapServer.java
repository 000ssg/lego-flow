package ssg.legoflow.network.ldap.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.ldap.codec.LdapCodec;
import ssg.legoflow.network.ldap.control.LdapControl;
import ssg.legoflow.network.ldap.protocol.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * LDAP v3 server that accepts connections and dispatches operations
 * to a {@link DirectoryBackend} (RFC 4511).
 *
 * <p>Uses virtual threads to handle each client connection concurrently.
 * The server is designed for testing and development purposes.
 *
 * <p>Usage example:
 * <pre>{@code
 * var backend = new InMemoryDirectoryBackend();
 * backend.addEntry("dc=example,dc=com", List.of(
 *     LdapAttribute.of("objectClass", "top", "domain"),
 *     LdapAttribute.of("dc", "example")
 * ));
 * try (var server = LdapServer.start(0, backend)) {
 *     int port = server.port();
 *     // ... use the server
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class LdapServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LdapServer.class);

    private final ServerSocket serverSocket;
    private final DirectoryBackend backend;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread acceptThread;

    private LdapServer(ServerSocket serverSocket, DirectoryBackend backend) {
        this.serverSocket = serverSocket;
        this.backend = backend;
        this.acceptThread = Thread.ofVirtual().name("ldap-accept").start(this::acceptLoop);
    }

    /**
     * Starts an LDAP server on the given port.
     *
     * @param port    the port to listen on (0 for auto-assign)
     * @param backend the directory backend
     * @return the started server
     * @throws IOException if the server cannot start
     */
    public static LdapServer start(int port, DirectoryBackend backend) throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress("127.0.0.1", port));
        LOG.info("LDAP server started on port {}", ss.getLocalPort());
        return new LdapServer(ss, backend);
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the local port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() throws IOException {
        if (running.compareAndSet(true, false)) {
            serverSocket.close();
            acceptThread.interrupt();
            LOG.info("LDAP server stopped");
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                Thread.ofVirtual()
                        .name("ldap-client-" + client.getRemoteSocketAddress())
                        .start(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        LOG.debug("Client connected: {}", client.getRemoteSocketAddress());
        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            while (!client.isClosed() && running.get()) {
                ByteBuffer buffer = readMessage(in);
                if (buffer == null) break;

                LdapMessage request = LdapCodec.decode(buffer);
                LOG.trace("Received: id={} op={}", request.messageId(),
                        request.protocolOp().getClass().getSimpleName());

                handleMessage(request, out);
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("Client disconnected: {}", e.getMessage());
            }
        }
    }

    private void handleMessage(LdapMessage request, OutputStream out) throws IOException {
        int msgId = request.messageId();
        switch (request.protocolOp()) {
            case BindRequest bind -> {
                LdapResult result = backend.bind(bind);
                sendResponse(out, msgId, new BindResponse(result, null), request.controls());
            }
            case UnbindRequest _ -> {
                // No response; connection will close
            }
            case SearchRequest search -> {
                List<SearchResultEntry> entries = backend.search(search);
                for (SearchResultEntry entry : entries) {
                    sendResponse(out, msgId, entry, List.of());
                }
                sendResponse(out, msgId, SearchResultDone.success(), request.controls());
            }
            case CompareRequest compare -> {
                boolean match = backend.compare(compare);
                CompareResponse resp = match ? CompareResponse.compareTrue() : CompareResponse.compareFalse();
                sendResponse(out, msgId, resp, request.controls());
            }
            case AddRequest add -> {
                LdapResult result = backend.add(add);
                sendResponse(out, msgId, new AddResponse(result), request.controls());
            }
            case DeleteRequest del -> {
                LdapResult result = backend.delete(del);
                sendResponse(out, msgId, new DeleteResponse(result), request.controls());
            }
            case ModifyRequest mod -> {
                LdapResult result = backend.modify(mod);
                sendResponse(out, msgId, new ModifyResponse(result), request.controls());
            }
            case ModifyDnRequest modDn -> {
                LdapResult result = backend.modifyDn(modDn);
                sendResponse(out, msgId, new ModifyDnResponse(result), request.controls());
            }
            case ExtendedRequest ext -> {
                ExtendedResponse resp = backend.extended(ext);
                sendResponse(out, msgId, resp, request.controls());
            }
            case AbandonRequest _ -> {
                // No response for abandon
            }
            default -> {
                LdapResult error = LdapResult.of(LdapResultCode.UNWILLING_TO_PERFORM,
                        "Unsupported operation");
                sendResponse(out, msgId, new ExtendedResponse(error, null, null),
                        request.controls());
            }
        }
    }

    private void sendResponse(OutputStream out, int msgId, LdapProtocolOp op,
                              List<LdapControl> controls) throws IOException {
        LdapMessage response = new LdapMessage(msgId, op, controls);
        byte[] encoded = LdapCodec.encodeToBytes(response);
        out.write(encoded);
        out.flush();
    }

    private ByteBuffer readMessage(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte < 0) return null;

        int secondByte = in.read();
        if (secondByte < 0) return null;

        int length;
        int headerSize;
        if (secondByte <= 127) {
            length = secondByte;
            headerSize = 2;
        } else {
            int numBytes = secondByte & 0x7F;
            headerSize = 2 + numBytes;
            length = 0;
            for (int i = 0; i < numBytes; i++) {
                int b = in.read();
                if (b < 0) return null;
                length = (length << 8) | b;
            }
        }

        byte[] content = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(content, offset, length - offset);
            if (read < 0) return null;
            offset += read;
        }

        ByteBuffer buffer = ByteBuffer.allocate(headerSize + length);
        buffer.put((byte) firstByte);
        if (secondByte <= 127) {
            buffer.put((byte) secondByte);
        } else {
            buffer.put((byte) secondByte);
            int numBytes = secondByte & 0x7F;
            for (int i = numBytes - 1; i >= 0; i--) {
                buffer.put((byte) ((length >> (8 * i)) & 0xFF));
            }
        }
        buffer.put(content);
        buffer.flip();
        return buffer;
    }
}
