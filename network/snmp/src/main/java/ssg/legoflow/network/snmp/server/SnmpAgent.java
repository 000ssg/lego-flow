package ssg.legoflow.network.snmp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.UsmEngine;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * SNMPv3 agent (server) that processes requests against an in-memory MIB tree.
 *
 * <p>Listens for SNMP requests on a UDP port, processes them against a
 * {@link MibTree}, and returns responses. Supports GET, GETNEXT, GETBULK,
 * and SET operations. Can also send traps and inform notifications.
 *
 * <p>Uses virtual threads for concurrent request handling.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.1.0
 */
public final class SnmpAgent implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpAgent.class);

    /** Default SNMP agent port. */
    public static final int DEFAULT_PORT = 161;

    private final DatagramSocket socket;
    private final MibTree mibTree;
    private final UsmEngine usmEngine;
    private volatile boolean running;
    private final AtomicInteger msgIdCounter = new AtomicInteger(1);

    /**
     * Creates an SNMP agent bound to the given port.
     *
     * @param port      the UDP port to listen on
     * @param mibTree   the MIB tree
     * @param usmEngine the USM engine for security
     * @throws IOException if the socket cannot be bound
     */
    public SnmpAgent(int port, MibTree mibTree, UsmEngine usmEngine) throws IOException {
        this.socket = new DatagramSocket(port);
        this.mibTree = mibTree;
        this.usmEngine = usmEngine;
        LOG.debug("SNMP agent bound to port {}", port);
    }

    /**
     * Creates an SNMP agent bound to a random available port.
     *
     * @param mibTree   the MIB tree
     * @param usmEngine the USM engine for security
     * @throws IOException if the socket cannot be created
     */
    public SnmpAgent(MibTree mibTree, UsmEngine usmEngine) throws IOException {
        this(0, mibTree, usmEngine);
    }

    /**
     * Returns the local port this agent is listening on.
     *
     * @return the local port
     */
    public int localPort() {
        return socket.getLocalPort();
    }

    /**
     * Returns the MIB tree.
     *
     * @return the MIB tree
     */
    public MibTree mibTree() {
        return mibTree;
    }

    /**
     * Returns the USM engine.
     *
     * @return the USM engine
     */
    public UsmEngine usmEngine() {
        return usmEngine;
    }

    /**
     * Starts the agent, listening for incoming requests.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Agent already running");
        }
        running = true;
        Thread.ofVirtual().name("snmp-agent").start(this::acceptLoop);
        LOG.info("SNMP agent started on port {}", socket.getLocalPort());
    }

    private void acceptLoop() {
        byte[] buf = new byte[65507];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                Thread.ofVirtual().name("snmp-request-handler").start(() ->
                        handleRequest(data, clientAddr, clientPort));
            } catch (IOException e) {
                if (running) {
                    LOG.error("Error receiving request", e);
                }
            }
        }
    }

    private void handleRequest(byte[] data, InetAddress clientAddr, int clientPort) {
        try {
            SnmpMessage request = SnmpCodec.decodeMessage(data);
            SnmpPdu requestPdu = request.scopedPdu().pdu();
            SnmpPdu responsePdu = processRequest(requestPdu);

            ScopedPdu responseScopedPdu = new ScopedPdu(
                    usmEngine.engineId(), "", responsePdu);

            SnmpMessage response = SnmpMessage.builder()
                    .msgId(request.msgId())
                    .msgMaxSize(65507)
                    .msgFlags(request.msgFlags())
                    .msgSecurityModel(request.msgSecurityModel())
                    .securityParams(request.securityParams())
                    .scopedPdu(responseScopedPdu)
                    .build();

            byte[] responseData = SnmpCodec.encodeMessage(response);
            DatagramPacket responsePacket = new DatagramPacket(
                    responseData, responseData.length, clientAddr, clientPort);
            socket.send(responsePacket);
        } catch (Exception e) {
            LOG.error("Error handling request from {}:{}", clientAddr, clientPort, e);
        }
    }

    /**
     * Processes an SNMP request PDU against the MIB tree.
     *
     * @param requestPdu the request PDU
     * @return the response PDU
     */
    public SnmpPdu processRequest(SnmpPdu requestPdu) {
        return switch (requestPdu) {
            case SnmpPdu.GetRequest req -> processGet(req);
            case SnmpPdu.GetNextRequest req -> processGetNext(req);
            case SnmpPdu.GetBulkRequest req -> processGetBulk(req);
            case SnmpPdu.SetRequest req -> processSet(req);
            case SnmpPdu.InformRequest req -> processInform(req);
            case SnmpPdu.Response _, SnmpPdu.TrapV2 _ ->
                    new SnmpPdu.Response(requestPdu.requestId(), 5, 0, VarBindList.empty());
        };
    }

    private SnmpPdu.Response processGet(SnmpPdu.GetRequest req) {
        List<VarBind> resultBindings = new ArrayList<>();
        int errorStatus = 0;
        int errorIndex = 0;

        for (int i = 0; i < req.varBindList().size(); i++) {
            VarBind vb = req.varBindList().get(i);
            SnmpValue value = mibTree.get(vb.oid());
            if (value != null) {
                resultBindings.add(new VarBind(vb.oid(), value));
            } else {
                resultBindings.add(new VarBind(vb.oid(), SnmpValue.NoSuchObject.INSTANCE));
            }
        }

        return new SnmpPdu.Response(req.requestId(), errorStatus, errorIndex,
                VarBindList.of(resultBindings));
    }

    private SnmpPdu.Response processGetNext(SnmpPdu.GetNextRequest req) {
        List<VarBind> resultBindings = new ArrayList<>();

        for (VarBind vb : req.varBindList()) {
            Map.Entry<ObjectIdentifier, SnmpValue> next = mibTree.getNext(vb.oid());
            if (next != null) {
                resultBindings.add(new VarBind(next.getKey(), next.getValue()));
            } else {
                resultBindings.add(new VarBind(vb.oid(), SnmpValue.EndOfMibView.INSTANCE));
            }
        }

        return new SnmpPdu.Response(req.requestId(), 0, 0,
                VarBindList.of(resultBindings));
    }

    private SnmpPdu.Response processGetBulk(SnmpPdu.GetBulkRequest req) {
        List<VarBind> resultBindings = new ArrayList<>();
        int nonRepeaters = Math.min(req.nonRepeaters(), req.varBindList().size());
        int maxReps = req.maxRepetitions();

        // Process non-repeaters (like GETNEXT)
        for (int i = 0; i < nonRepeaters; i++) {
            VarBind vb = req.varBindList().get(i);
            Map.Entry<ObjectIdentifier, SnmpValue> next = mibTree.getNext(vb.oid());
            if (next != null) {
                resultBindings.add(new VarBind(next.getKey(), next.getValue()));
            } else {
                resultBindings.add(new VarBind(vb.oid(), SnmpValue.EndOfMibView.INSTANCE));
            }
        }

        // Process repeaters
        int repeaterCount = req.varBindList().size() - nonRepeaters;
        if (repeaterCount > 0) {
            ObjectIdentifier[] currentOids = new ObjectIdentifier[repeaterCount];
            for (int i = 0; i < repeaterCount; i++) {
                currentOids[i] = req.varBindList().get(nonRepeaters + i).oid();
            }

            for (int rep = 0; rep < maxReps; rep++) {
                boolean allEndOfMib = true;
                for (int i = 0; i < repeaterCount; i++) {
                    Map.Entry<ObjectIdentifier, SnmpValue> next = mibTree.getNext(currentOids[i]);
                    if (next != null) {
                        resultBindings.add(new VarBind(next.getKey(), next.getValue()));
                        currentOids[i] = next.getKey();
                        allEndOfMib = false;
                    } else {
                        resultBindings.add(new VarBind(currentOids[i],
                                SnmpValue.EndOfMibView.INSTANCE));
                    }
                }
                if (allEndOfMib) break;
            }
        }

        return new SnmpPdu.Response(req.requestId(), 0, 0,
                VarBindList.of(resultBindings));
    }

    private SnmpPdu.Response processSet(SnmpPdu.SetRequest req) {
        // Validate all bindings first (two-phase commit)
        for (int i = 0; i < req.varBindList().size(); i++) {
            VarBind vb = req.varBindList().get(i);
            if (vb.value() instanceof SnmpValue.Null) {
                // Error: cannot set null value
                return new SnmpPdu.Response(req.requestId(), 17, i + 1,
                        req.varBindList()); // notWritable
            }
        }

        // Apply all changes
        for (VarBind vb : req.varBindList()) {
            mibTree.put(vb.oid(), vb.value());
        }

        return new SnmpPdu.Response(req.requestId(), 0, 0, req.varBindList());
    }

    private SnmpPdu.Response processInform(SnmpPdu.InformRequest req) {
        // Acknowledge the inform with a response
        return new SnmpPdu.Response(req.requestId(), 0, 0, req.varBindList());
    }

    // ── Trap/Inform Sending ──

    /**
     * Sends a TrapV2 notification to the specified target.
     *
     * @param targetHost the target hostname or IP
     * @param targetPort the target port (typically 162)
     * @param sysUpTime  the sysUpTime value in hundredths of a second
     * @param trapOid    the trap OID
     * @param varBinds   additional variable bindings
     * @throws IOException if sending fails
     */
    public void sendTrapV2(String targetHost, int targetPort, long sysUpTime,
                            ObjectIdentifier trapOid, VarBind... varBinds)
            throws IOException {
        List<VarBind> allBinds = new ArrayList<>();
        allBinds.add(new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(sysUpTime)));
        allBinds.add(new VarBind(SnmpOids.SNMP_TRAP_OID, new SnmpValue.Oid(trapOid)));
        allBinds.addAll(List.of(varBinds));

        SnmpPdu trapPdu = new SnmpPdu.TrapV2(0, 0, 0, VarBindList.of(allBinds));
        ScopedPdu scopedPdu = new ScopedPdu(usmEngine.engineId(), "", trapPdu);

        SnmpMessage msg = SnmpMessage.builder()
                .msgId(msgIdCounter.getAndIncrement())
                .msgMaxSize(65507)
                .scopedPdu(scopedPdu)
                .build();

        byte[] data = SnmpCodec.encodeMessage(msg);
        InetAddress address = InetAddress.getByName(targetHost);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
        socket.send(packet);
        LOG.debug("Sent TrapV2 to {}:{}", targetHost, targetPort);
    }

    /**
     * Sends an InformRequest notification to the specified target.
     *
     * @param targetHost the target hostname or IP
     * @param targetPort the target port
     * @param sysUpTime  the sysUpTime value
     * @param trapOid    the trap OID
     * @param varBinds   additional variable bindings
     * @throws IOException if sending fails
     */
    public void sendInform(String targetHost, int targetPort, long sysUpTime,
                            ObjectIdentifier trapOid, VarBind... varBinds)
            throws IOException {
        List<VarBind> allBinds = new ArrayList<>();
        allBinds.add(new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(sysUpTime)));
        allBinds.add(new VarBind(SnmpOids.SNMP_TRAP_OID, new SnmpValue.Oid(trapOid)));
        allBinds.addAll(List.of(varBinds));

        SnmpPdu informPdu = new SnmpPdu.InformRequest(
                msgIdCounter.getAndIncrement(), 0, 0, VarBindList.of(allBinds));
        ScopedPdu scopedPdu = new ScopedPdu(usmEngine.engineId(), "", informPdu);

        SnmpMessage msg = SnmpMessage.builder()
                .msgId(msgIdCounter.getAndIncrement())
                .msgMaxSize(65507)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .build();

        byte[] data = SnmpCodec.encodeMessage(msg);
        InetAddress address = InetAddress.getByName(targetHost);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
        socket.send(packet);
        LOG.debug("Sent InformRequest to {}:{}", targetHost, targetPort);
    }

    @Override
    public void close() {
        running = false;
        if (!socket.isClosed()) {
            socket.close();
        }
        LOG.debug("SNMP agent closed");
    }
}
