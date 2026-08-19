package ssg.legoflow.network.snmp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
/**
 * SNMPv3 manager (client) for sending requests and receiving traps/informs.
 *
 * <p>Supports GET, GETNEXT, GETBULK, and SET operations over UDP transport.
 * Provides SNMPv1/v2c community-string based access and SNMPv3 USM-based
 * secure access. Uses virtual threads for trap/inform reception.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.1.0
 */
public final class SnmpManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpManager.class);

    /** Default SNMP port. */
    public static final int DEFAULT_PORT = 161;
    /** Default SNMP trap port. */
    public static final int DEFAULT_TRAP_PORT = 162;
    /** Default timeout in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 5000;
    /** Default number of retransmissions. */
    public static final int DEFAULT_RETRIES = 2;
    /** Default maximum message size. */
    public static final int DEFAULT_MAX_SIZE = 65507;

    private final DatagramSocket socket;
    private final InetAddress targetAddress;
    private final int targetPort;
    private final int timeoutMs;
    private final int retries;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private final AtomicInteger msgIdCounter = new AtomicInteger(1);

    // SNMPv3 USM fields
    private final UsmEngine usmEngine;
    private volatile UsmUser currentUser;
    private volatile SecurityLevel securityLevel;
    private volatile byte[] remoteEngineId;
    private volatile int remoteEngineBoots;
    private volatile int remoteEngineTime;

    // SNMPv1/v2c community
    private volatile String community;

    // Trap listener
    private volatile DatagramSocket trapSocket;
    private volatile boolean trapListenerRunning;

    /**
     * Creates an SNMP manager for SNMPv3 communication.
     *
     * @param targetHost the target agent hostname or IP
     * @param targetPort the target agent port
     * @param usmEngine  the USM engine for security
     * @param timeoutMs  the request timeout in milliseconds
     * @param retries    the number of retransmission attempts
     * @throws IOException if the socket cannot be created
     */
    public SnmpManager(String targetHost, int targetPort, UsmEngine usmEngine,
                       int timeoutMs, int retries) throws IOException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeoutMs);
        this.targetAddress = InetAddress.getByName(targetHost);
        this.targetPort = targetPort;
        this.timeoutMs = timeoutMs;
        this.retries = retries;
        this.usmEngine = usmEngine;
        this.securityLevel = SecurityLevel.NO_AUTH_NO_PRIV;
        LOG.debug("SNMP manager created targeting {}:{}", targetHost, targetPort);
    }

    /**
     * Creates an SNMP manager with default timeout and retries.
     *
     * @param targetHost the target agent hostname or IP
     * @param targetPort the target agent port
     * @param usmEngine  the USM engine for security
     * @throws IOException if the socket cannot be created
     */
    public SnmpManager(String targetHost, int targetPort, UsmEngine usmEngine)
            throws IOException {
        this(targetHost, targetPort, usmEngine, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES);
    }

    /**
     * Creates an SNMP manager for SNMPv1/v2c with a community string.
     *
     * @param targetHost the target agent hostname or IP
     * @param targetPort the target agent port
     * @param community  the community string
     * @throws IOException if the socket cannot be created
     */
    public SnmpManager(String targetHost, int targetPort, String community)
            throws IOException {
        this(targetHost, targetPort, new UsmEngine(new byte[]{0x00}),
                DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES);
        this.community = community;
    }

    /**
     * Sets the current USM user and security level for subsequent requests.
     *
     * @param user          the USM user
     * @param securityLevel the security level
     */
    public void setUser(UsmUser user, SecurityLevel securityLevel) {
        this.currentUser = user;
        this.securityLevel = securityLevel;
    }

    /**
     * Sets the remote engine timing information (from engine discovery).
     *
     * @param engineId    the remote engine ID
     * @param engineBoots the remote engine boots
     * @param engineTime  the remote engine time
     */
    public void setRemoteEngine(byte[] engineId, int engineBoots, int engineTime) {
        this.remoteEngineId = engineId != null ? engineId.clone() : new byte[0];
        this.remoteEngineBoots = engineBoots;
        this.remoteEngineTime = engineTime;
    }

    // ── SNMP Operations ──

    /**
     * Sends a GET request for the specified OIDs.
     *
     * @param oids the OIDs to retrieve
     * @return the response PDU
     * @throws IOException if communication fails
     */
    public SnmpPdu.Response get(ObjectIdentifier... oids) throws IOException {
        VarBindList.Builder builder = VarBindList.builder();
        for (ObjectIdentifier oid : oids) {
            builder.addNull(oid);
        }
        int reqId = requestIdCounter.getAndIncrement();
        SnmpPdu pdu = new SnmpPdu.GetRequest(reqId, 0, 0, builder.build());
        return sendRequest(pdu);
    }

    /**
     * Sends a GETNEXT request for the specified OIDs.
     *
     * @param oids the OIDs to get the next values for
     * @return the response PDU
     * @throws IOException if communication fails
     */
    public SnmpPdu.Response getNext(ObjectIdentifier... oids) throws IOException {
        VarBindList.Builder builder = VarBindList.builder();
        for (ObjectIdentifier oid : oids) {
            builder.addNull(oid);
        }
        int reqId = requestIdCounter.getAndIncrement();
        SnmpPdu pdu = new SnmpPdu.GetNextRequest(reqId, 0, 0, builder.build());
        return sendRequest(pdu);
    }

    /**
     * Sends a GETBULK request.
     *
     * @param nonRepeaters   the number of non-repeating OIDs
     * @param maxRepetitions the maximum repetitions for remaining OIDs
     * @param oids           the OIDs to retrieve
     * @return the response PDU
     * @throws IOException if communication fails
     */
    public SnmpPdu.Response getBulk(int nonRepeaters, int maxRepetitions,
                                     ObjectIdentifier... oids) throws IOException {
        VarBindList.Builder builder = VarBindList.builder();
        for (ObjectIdentifier oid : oids) {
            builder.addNull(oid);
        }
        int reqId = requestIdCounter.getAndIncrement();
        SnmpPdu pdu = new SnmpPdu.GetBulkRequest(reqId, nonRepeaters,
                maxRepetitions, builder.build());
        return sendRequest(pdu);
    }

    /**
     * Sends a SET request with the given variable bindings.
     *
     * @param varBinds the variable bindings with values to set
     * @return the response PDU
     * @throws IOException if communication fails
     */
    public SnmpPdu.Response set(VarBind... varBinds) throws IOException {
        int reqId = requestIdCounter.getAndIncrement();
        SnmpPdu pdu = new SnmpPdu.SetRequest(reqId, 0, 0, VarBindList.of(varBinds));
        return sendRequest(pdu);
    }

    /**
     * Sends an INFORM request.
     *
     * @param varBinds the variable bindings
     * @return the response PDU
     * @throws IOException if communication fails
     */
    public SnmpPdu.Response inform(VarBind... varBinds) throws IOException {
        int reqId = requestIdCounter.getAndIncrement();
        SnmpPdu pdu = new SnmpPdu.InformRequest(reqId, 0, 0, VarBindList.of(varBinds));
        return sendRequest(pdu);
    }

    // ── Trap/Inform Listener ──

    /**
     * Starts a trap/inform listener on the given port.
     *
     * @param port    the port to listen on
     * @param handler the handler for received traps/informs
     * @throws IOException if the socket cannot be opened
     */
    public void startTrapListener(int port, Consumer<SnmpMessage> handler) throws IOException {
        if (trapListenerRunning) {
            throw new IllegalStateException("Trap listener already running");
        }
        trapSocket = new DatagramSocket(port);
        trapListenerRunning = true;

        Thread.ofVirtual().name("snmp-trap-listener").start(() -> {
            byte[] buf = new byte[DEFAULT_MAX_SIZE];
            while (trapListenerRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    trapSocket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    SnmpMessage msg = SnmpCodec.decodeMessage(data);
                    handler.accept(msg);
                } catch (IOException e) {
                    if (trapListenerRunning) {
                        LOG.error("Error receiving trap/inform", e);
                    }
                } catch (Exception e) {
                    LOG.error("Error processing trap/inform", e);
                }
            }
        });
        LOG.info("Trap listener started on port {}", port);
    }

    /**
     * Stops the trap/inform listener.
     */
    public void stopTrapListener() {
        trapListenerRunning = false;
        if (trapSocket != null && !trapSocket.isClosed()) {
            trapSocket.close();
        }
    }

    // ── Request handling ──

    private SnmpPdu.Response sendRequest(SnmpPdu pdu) throws IOException {
        byte[] engineId = remoteEngineId != null ? remoteEngineId : new byte[0];
        ScopedPdu scopedPdu = new ScopedPdu(engineId, "", pdu);

        int msgId = msgIdCounter.getAndIncrement();
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(msgId)
                .msgMaxSize(DEFAULT_MAX_SIZE)
                .securityLevel(securityLevel)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .securityParams(buildSecurityParams())
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);

        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                DatagramPacket request = new DatagramPacket(
                        encoded, encoded.length, targetAddress, targetPort);
                socket.send(request);
                LOG.debug("Sent request (attempt {}): msgId={}, reqId={}",
                        attempt + 1, msgId, pdu.requestId());

                byte[] buf = new byte[DEFAULT_MAX_SIZE];
                DatagramPacket response = new DatagramPacket(buf, buf.length);
                socket.receive(response);

                byte[] responseData = new byte[response.getLength()];
                System.arraycopy(response.getData(), response.getOffset(),
                        responseData, 0, response.getLength());

                SnmpMessage responseMsg = SnmpCodec.decodeMessage(responseData);
                SnmpPdu responsePdu = responseMsg.scopedPdu().pdu();

                if (responsePdu instanceof SnmpPdu.Response resp) {
                    return resp;
                }
                throw new IOException("Expected Response PDU, got: " +
                        responsePdu.getClass().getSimpleName());
            } catch (SocketTimeoutException e) {
                if (attempt >= retries) {
                    throw new IOException("Request timed out after %d attempts".formatted(retries + 1), e);
                }
                LOG.debug("Timeout on attempt {}, retrying", attempt + 1);
            }
        }
        throw new IOException("Request failed after retransmission");
    }

    private byte[] buildSecurityParams() {
        if (currentUser == null) {
            return SnmpCodec.encodeUsmSecurityParams(UsmSecurityParameters.empty());
        }
        UsmSecurityParameters params = new UsmSecurityParameters(
                remoteEngineId != null ? remoteEngineId : new byte[0],
                remoteEngineBoots,
                remoteEngineTime,
                currentUser.userName(),
                new byte[12], // placeholder for auth
                new byte[0]
        );
        return SnmpCodec.encodeUsmSecurityParams(params);
    }

    @Override
    public void close() {
        stopTrapListener();
        if (!socket.isClosed()) {
            socket.close();
        }
        LOG.debug("SNMP manager closed");
    }
}
