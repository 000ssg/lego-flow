package ssg.legoflow.network.modbus.client;

import ssg.legoflow.network.modbus.protocol.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Low-level Modbus TCP connection for sending/receiving frames.
 *
 * @since 0.1.0
 */
public final class ModbusConnection implements AutoCloseable {

    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicInteger transactionCounter = new AtomicInteger(0);

    /**
     * Creates a connection to the given host and port.
     *
     * @param host the server hostname
     * @param port the server port
     * @throws IOException if connection fails
     */
    public ModbusConnection(String host, int port) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /**
     * Sends a request PDU and returns the response frame.
     *
     * @param unitId the unit ID
     * @param pdu    the request PDU
     * @return the response frame
     * @throws IOException     if communication fails
     * @throws ModbusException if the response is an exception
     */
    public ModbusFrame sendRequest(int unitId, byte[] pdu) throws IOException {
        int txId = transactionCounter.incrementAndGet() & 0xFFFF;
        var header = MbapHeader.request(txId, unitId, pdu.length);
        var request = new ModbusFrame(header, pdu);
        ModbusCodec.write(request, out);

        ModbusFrame response = ModbusCodec.read(in);

        if (response.isException()) {
            byte[] data = response.data();
            int exCode = data.length > 0 ? data[0] & 0xFF : 0;
            throw new ModbusException(
                    ModbusException.ExceptionCode.of(exCode),
                    "Modbus exception: " + ModbusException.ExceptionCode.of(exCode));
        }

        return response;
    }

    /**
     * Returns whether the connection is open.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
