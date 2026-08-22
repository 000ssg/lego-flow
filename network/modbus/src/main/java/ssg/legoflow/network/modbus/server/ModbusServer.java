package ssg.legoflow.network.modbus.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.modbus.protocol.MbapHeader;
import ssg.legoflow.network.modbus.protocol.ModbusCodec;
import ssg.legoflow.network.modbus.protocol.ModbusFrame;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
/**
 * Modbus TCP server using virtual threads.
 *
 * <p>Accepts TCP connections and processes Modbus requests against a
 * {@link DeviceMemory} instance. Each connection is handled in its
 * own virtual thread.
 *
 * @since 0.1.0
 */
public final class ModbusServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusServer.class);
    /** Default Modbus TCP port. */
    public static final int DEFAULT_PORT = 502;

    private final ServerSocket serverSocket;
    private final RequestHandler handler;
    private volatile boolean running;

    /**
     * Creates a Modbus server with its own device memory.
     *
     * @param port the port to listen on
     * @throws IOException if binding fails
     */
    public ModbusServer(int port) throws IOException {
        this(port, new DeviceMemory());
    }

    /**
     * Creates a Modbus server with the given device memory.
     *
     * @param port   the port to listen on
     * @param memory the device memory
     * @throws IOException if binding fails
     */
    public ModbusServer(int port, DeviceMemory memory) throws IOException {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(port));
        this.handler = new RequestHandler(memory);
        LOG.debug("Modbus server bound to port {}", port);
    }

    /**
     * Returns the local port this server is listening on.
     *
     * @return the local port
     */
    public int localPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the request handler.
     *
     * @return the handler
     */
    public RequestHandler handler() {
        return handler;
    }

    /**
     * Starts accepting connections.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Server already running");
        }
        running = true;
        Thread.ofVirtual().name("modbus-acceptor").start(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    Thread.ofVirtual().name("modbus-client").start(() ->
                            handleConnection(client));
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Error accepting connection", e);
                    }
                }
            }
        });
        LOG.info("Modbus server started on port {}", serverSocket.getLocalPort());
    }

    private void handleConnection(Socket client) {
        try (client;
             InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            while (running && !client.isClosed()) {
                ModbusFrame request;
                try {
                    request = ModbusCodec.read(in);
                } catch (Exception e) {
                    break;
                }

                byte[] responsePdu = handler.handle(request.pdu());
                var responseHeader = MbapHeader.request(
                        request.header().transactionId(),
                        request.header().unitId(),
                        responsePdu.length);
                var response = new ModbusFrame(responseHeader, responsePdu);
                ModbusCodec.write(response, out);
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("Connection error", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        LOG.debug("Modbus server closed");
    }
}
