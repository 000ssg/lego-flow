package ssg.legoflow.coap.discovery;

import ssg.legoflow.coap.codec.CoapCodec;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CoAP resource discovery using the {@code /.well-known/core} endpoint (RFC 6690)
 * and CoAP multicast (RFC 7252, Section 8).
 *
 * @since 0.1.0
 */
public final class CoapDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(CoapDiscovery.class);

    /** CoAP "All CoAP Nodes" multicast address (IPv4). */
    public static final String COAP_MULTICAST_IPV4 = "224.0.1.187";

    /** Default CoAP port. */
    public static final int DEFAULT_PORT = 5683;

    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private static final CoapCodec CODEC = new CoapCodec();

    private CoapDiscovery() {
        // Utility class
    }

    /**
     * Discovers resources at the given host and port by querying {@code /.well-known/core}.
     *
     * @param host the target host
     * @param port the target port
     * @return the discovered resources
     * @throws IOException if the request fails
     * @since 0.1.0
     */
    public static List<LinkFormatEntry> discoverResources(String host, int port) throws IOException {
        return discoverWithQuery(host, port, null);
    }

    /**
     * Discovers resources by type at the given host and port.
     *
     * @param host         the target host
     * @param port         the target port
     * @param resourceType the resource type to filter by (rt=)
     * @return the discovered resources matching the type
     * @throws IOException if the request fails
     * @since 0.1.0
     */
    public static List<LinkFormatEntry> discoverByType(String host, int port, String resourceType) throws IOException {
        return discoverWithQuery(host, port, "rt=" + resourceType);
    }

    /**
     * Discovers resources via CoAP multicast on 224.0.1.187.
     *
     * <p>Sends a NON GET to {@code /.well-known/core} via multicast and collects
     * responses within a timeout period.
     *
     * @return the discovered resources from all responding servers
     * @throws IOException if the multicast request fails
     * @since 0.1.0
     */
    public static List<LinkFormatEntry> multicastDiscover() throws IOException {
        return multicastDiscover(DISCOVERY_TIMEOUT_MS);
    }

    /**
     * Discovers resources via CoAP multicast with a custom timeout.
     *
     * @param timeoutMs the timeout in milliseconds to wait for responses
     * @return the discovered resources from all responding servers
     * @throws IOException if the multicast request fails
     * @since 0.1.0
     */
    public static List<LinkFormatEntry> multicastDiscover(int timeoutMs) throws IOException {
        var message = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(ThreadLocalRandom.current().nextInt(0, 0xFFFF))
                .token(generateToken())
                .uriPath("/.well-known/core")
                .build();

        var buffer = CODEC.encode(message);
        var target = new InetSocketAddress(COAP_MULTICAST_IPV4, DEFAULT_PORT);

        try (var channel = DatagramChannel.open(StandardProtocolFamily.INET)) {
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            channel.configureBlocking(true);
            channel.socket().setSoTimeout(timeoutMs);

            channel.send(buffer, target);
            LOG.debug("Sent multicast discovery to {}", target);

            var entries = new ArrayList<LinkFormatEntry>();
            var recvBuf = ByteBuffer.allocate(2048);
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline) {
                try {
                    recvBuf.clear();
                    channel.socket().setSoTimeout((int) Math.max(1, deadline - System.currentTimeMillis()));
                    var source = channel.receive(recvBuf);
                    if (source == null) continue;

                    recvBuf.flip();
                    var response = CODEC.decode(recvBuf);

                    if (response.code().isSuccess() && response.hasPayload()) {
                        var parsed = LinkFormatParser.parse(response.getPayloadString());
                        entries.addAll(parsed);
                    }
                } catch (IOException e) {
                    // Timeout or other error, stop collecting
                    break;
                }
            }

            return entries;
        }
    }

    private static List<LinkFormatEntry> discoverWithQuery(String host, int port, String query) throws IOException {
        var builder = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(ThreadLocalRandom.current().nextInt(0, 0xFFFF))
                .token(generateToken())
                .uriPath("/.well-known/core");

        if (query != null) {
            builder.uriQuery(query);
        }

        var message = builder.build();
        var buffer = CODEC.encode(message);
        var target = new InetSocketAddress(host, port);

        try (var channel = DatagramChannel.open()) {
            channel.configureBlocking(true);
            channel.socket().setSoTimeout(DISCOVERY_TIMEOUT_MS);

            channel.send(buffer, target);
            LOG.debug("Sent discovery request to {}", target);

            var recvBuf = ByteBuffer.allocate(2048);
            var source = channel.receive(recvBuf);
            if (source == null) {
                return Collections.emptyList();
            }

            recvBuf.flip();
            var response = CODEC.decode(recvBuf);

            if (response.code().isSuccess() && response.hasPayload()) {
                return LinkFormatParser.parse(response.getPayloadString());
            }

            return Collections.emptyList();
        }
    }

    private static byte[] generateToken() {
        var token = new byte[4];
        ThreadLocalRandom.current().nextBytes(token);
        return token;
    }
}
