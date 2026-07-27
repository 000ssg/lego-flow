package ssg.legoflow.network.dns.transport;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * DNS-over-HTTPS transport (RFC 8484).
 *
 * <p>Supports both HTTP POST (with {@code application/dns-message} content type)
 * and HTTP GET (with base64url-encoded query parameter).
 *
 * @since 1.0.0
 */
public final class DohTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DohTransport.class);
    private static final String DNS_MESSAGE_TYPE = "application/dns-message";

    private final URI serverUri;
    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Creates a DoH transport.
     *
     * @param serverUri the DoH server URI (e.g., "https://dns.google/dns-query")
     * @param timeout   the request timeout
     * @since 1.0.0
     */
    public DohTransport(URI serverUri, Duration timeout) {
        this.serverUri = Objects.requireNonNull(serverUri, "serverUri must not be null");
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Creates a DoH transport with a custom HTTP client.
     *
     * @param serverUri  the DoH server URI
     * @param httpClient the HTTP client to use
     * @param timeout    the request timeout
     * @since 1.0.0
     */
    public DohTransport(URI serverUri, HttpClient httpClient, Duration timeout) {
        this.serverUri = Objects.requireNonNull(serverUri);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.timeout = timeout;
    }

    /**
     * Sends a DNS query using HTTP POST.
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage sendPost(DnsMessage query) throws IOException {
        byte[] data = DnsCodec.encode(query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(serverUri)
                .header("Content-Type", DNS_MESSAGE_TYPE)
                .header("Accept", DNS_MESSAGE_TYPE)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        LOG.debug("Sending DoH POST to {}", serverUri);
        return executeRequest(request);
    }

    /**
     * Sends a DNS query using HTTP GET with base64url encoding.
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage sendGet(DnsMessage query) throws IOException {
        byte[] data = DnsCodec.encode(query);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(data);

        URI uri = URI.create(serverUri.toString() + "?dns=" + encoded);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", DNS_MESSAGE_TYPE)
                .timeout(timeout)
                .GET()
                .build();

        LOG.debug("Sending DoH GET to {}", uri);
        return executeRequest(request);
    }

    /**
     * Sends a DNS query using the default method (POST).
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage send(DnsMessage query) throws IOException {
        return sendPost(query);
    }

    private DnsMessage executeRequest(HttpRequest request) throws IOException {
        try {
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("DoH server returned HTTP " + response.statusCode());
            }

            byte[] body = response.body();
            LOG.debug("Received DoH response ({} bytes)", body.length);
            return DnsCodec.decode(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("DoH request interrupted", e);
        }
    }

    @Override
    public void close() {
        // HttpClient does not require explicit close in JDK 25
    }
}
