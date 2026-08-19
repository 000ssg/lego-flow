package ssg.legoflow.network.dns.server;

import ssg.legoflow.network.dns.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 * DNS server supporting both UDP and TCP transports.
 *
 * <p>Uses virtual threads for handling concurrent requests. Delegates
 * query handling to a {@link DnsHandler} implementation, which can be
 * an {@link AuthoritativeZone} or a custom handler.
 *
 * @since 0.1.0
 */
public final class DnsServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DnsServer.class);

    private final InetSocketAddress bindAddress;
    private final DnsHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong queriesReceived = new AtomicLong();
    private final AtomicLong responsesSent = new AtomicLong();
    private final Map<DnsName, AuthoritativeZone> zones = new ConcurrentHashMap<>();

    private DatagramChannel udpChannel;
    private ServerSocket tcpSocket;
    private ExecutorService executor;
    private Thread udpThread;
    private Thread tcpThread;

    /**
     * Creates a DNS server.
     *
     * @param bindAddress the address to bind to
     * @param handler     the query handler
     * @since 0.1.0
     */
    public DnsServer(InetSocketAddress bindAddress, DnsHandler handler) {
        this.bindAddress = Objects.requireNonNull(bindAddress);
        this.handler = Objects.requireNonNull(handler);
    }

    /**
     * Creates a DNS server with a zone-based handler.
     *
     * @param bindAddress the address to bind to
     * @since 0.1.0
     */
    public DnsServer(InetSocketAddress bindAddress) {
        this.bindAddress = Objects.requireNonNull(bindAddress);
        this.handler = this::handleWithZones;
    }

    /**
     * Adds an authoritative zone to the server.
     *
     * @param zone the zone to add
     * @since 0.1.0
     */
    public void addZone(AuthoritativeZone zone) {
        zones.put(zone.origin(), zone);
    }

    /**
     * Starts the server on both UDP and TCP.
     *
     * @throws IOException if the server cannot start
     * @since 0.1.0
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already running");
        }

        executor = Executors.newVirtualThreadPerTaskExecutor();

        // Start UDP
        udpChannel = DatagramChannel.open();
        udpChannel.bind(bindAddress);
        LOG.info("DNS server listening on UDP {}", bindAddress);

        udpThread = Thread.ofVirtual().name("dns-udp").start(this::udpLoop);

        // Start TCP
        tcpSocket = new ServerSocket();
        tcpSocket.bind(bindAddress);
        LOG.info("DNS server listening on TCP {}", bindAddress);

        tcpThread = Thread.ofVirtual().name("dns-tcp").start(this::tcpLoop);
    }

    /**
     * Returns the actual bound address (useful when binding to port 0).
     *
     * @return the bound address, or {@code null} if not started
     * @since 0.1.0
     */
    public InetSocketAddress boundAddress() {
        if (udpChannel != null) {
            try {
                return (InetSocketAddress) udpChannel.getLocalAddress();
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the number of queries received.
     *
     * @return the query count
     * @since 0.1.0
     */
    public long queriesReceived() {
        return queriesReceived.get();
    }

    /**
     * Returns the number of responses sent.
     *
     * @return the response count
     * @since 0.1.0
     */
    public long responsesSent() {
        return responsesSent.get();
    }

    /**
     * Returns whether the server is running.
     *
     * @return {@code true} if running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) return;

        LOG.info("Stopping DNS server");

        if (udpChannel != null) udpChannel.close();
        if (tcpSocket != null) tcpSocket.close();
        if (executor != null) executor.shutdownNow();

        if (udpThread != null) udpThread.interrupt();
        if (tcpThread != null) tcpThread.interrupt();
    }

    private void udpLoop() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        while (running.get()) {
            try {
                buf.clear();
                SocketAddress sender = udpChannel.receive(buf);
                buf.flip();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);

                executor.submit(() -> handleUdpQuery(data, sender));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("UDP receive error", e);
                }
            }
        }
    }

    private void handleUdpQuery(byte[] data, SocketAddress sender) {
        try {
            queriesReceived.incrementAndGet();
            DnsMessage query = DnsCodec.decode(data);
            DnsMessage response = handler.handle(query, sender);

            byte[] responseData = DnsCodec.encode(response);

            // Check truncation for UDP
            if (responseData.length > DnsCodec.MAX_UDP_SIZE) {
                response = DnsMessage.builder()
                        .header(response.header().toBuilder()
                                .tc(true)
                                .anCount(0).nsCount(0).arCount(0)
                                .build())
                        .questions(response.questions())
                        .build();
                responseData = DnsCodec.encode(response);
            }

            udpChannel.send(ByteBuffer.wrap(responseData), sender);
            responsesSent.incrementAndGet();
        } catch (Exception e) {
            LOG.error("Error handling UDP query from {}", sender, e);
        }
    }

    private void tcpLoop() {
        while (running.get()) {
            try {
                Socket client = tcpSocket.accept();
                executor.submit(() -> handleTcpClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("TCP accept error", e);
                }
            }
        }
    }

    private void handleTcpClient(Socket client) {
        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // Read 2-byte length prefix
            int hi = in.read();
            int lo = in.read();
            if (hi < 0 || lo < 0) return;
            int len = (hi << 8) | lo;

            byte[] data = in.readNBytes(len);
            queriesReceived.incrementAndGet();

            DnsMessage query = DnsCodec.decode(data);
            DnsMessage response = handler.handle(query, client.getRemoteSocketAddress());
            byte[] responseData = DnsCodec.encode(response);

            // Write 2-byte length prefix + response
            out.write((responseData.length >> 8) & 0xFF);
            out.write(responseData.length & 0xFF);
            out.write(responseData);
            out.flush();
            responsesSent.incrementAndGet();
        } catch (Exception e) {
            LOG.error("Error handling TCP client", e);
        }
    }

    private DnsMessage handleWithZones(DnsMessage query, SocketAddress sender) {
        if (query.questions().isEmpty()) {
            return DnsMessage.responseFor(query, ResponseCode.FORMERR).build();
        }

        DnsQuestion q = query.questions().get(0);

        // Find the best matching zone
        DnsName name = q.name();
        while (!name.isRoot()) {
            AuthoritativeZone zone = zones.get(name);
            if (zone != null) {
                return zone.handleQuery(query);
            }
            name = name.parent();
        }

        return DnsMessage.responseFor(query, ResponseCode.REFUSED).build();
    }
}
