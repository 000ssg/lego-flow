package ssg.legoflow.upnp.soap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
/**
 * Client for invoking UPnP SOAP actions over HTTP.
 *
 * <p>Sends SOAP requests to UPnP service control URLs and parses the responses.
 * Supports both synchronous and asynchronous invocation using virtual threads.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.1.0
 */
public class SoapClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SoapClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Creates a new {@code SoapClient} with default timeout.
     *
     * @since 0.1.0
     */
    public SoapClient() {
        this(DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new {@code SoapClient} with the specified timeout.
     *
     * @param timeout the request timeout
     * @throws NullPointerException if {@code timeout} is {@code null}
     * @since 0.1.0
     */
    public SoapClient(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Creates a new {@code SoapClient} with a pre-configured HTTP client.
     *
     * <p>This constructor is primarily intended for testing.
     *
     * @param httpClient the HTTP client to use
     * @param timeout    the request timeout
     * @since 0.1.0
     */
    SoapClient(HttpClient httpClient, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    /**
     * Invokes a UPnP SOAP action synchronously.
     *
     * @param controlUrl  the service control URL
     * @param serviceType the service type URN
     * @param actionName  the action name to invoke
     * @param args        the input arguments
     * @return the SOAP response containing output arguments or fault
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 0.1.0
     */
    public SoapResponse invoke(URI controlUrl, String serviceType, String actionName,
                               Map<String, String> args) throws IOException, InterruptedException {
        Objects.requireNonNull(controlUrl, "controlUrl must not be null");
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        Objects.requireNonNull(actionName, "actionName must not be null");
        Objects.requireNonNull(args, "args must not be null");

        var soapMessage = SoapMessage.request(serviceType, actionName, args);
        var body = soapMessage.serializeRequest();
        var soapAction = SoapConstants.soapAction(serviceType, actionName);

        LOG.debug("Invoking SOAP action {} on {}", actionName, controlUrl);

        var request = HttpRequest.newBuilder()
                .uri(controlUrl)
                .timeout(timeout)
                .header("Content-Type", SoapConstants.CONTENT_TYPE)
                .header("SOAPAction", soapAction)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var responseBody = response.body();

        LOG.debug("SOAP response status {} for action {}", response.statusCode(), actionName);
        return SoapMessage.parseResponse(responseBody);
    }

    /**
     * Invokes a UPnP SOAP action asynchronously using a virtual thread.
     *
     * @param controlUrl  the service control URL
     * @param serviceType the service type URN
     * @param actionName  the action name to invoke
     * @param args        the input arguments
     * @return a future that completes with the SOAP response
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 0.1.0
     */
    public CompletableFuture<SoapResponse> invokeAsync(URI controlUrl, String serviceType,
                                                       String actionName, Map<String, String> args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(controlUrl, serviceType, actionName, args);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("SOAP invocation failed for action: " + actionName, e);
            }
        }, runnable -> Thread.ofVirtual().name("soap-invoke-" + actionName).start(runnable));
    }

    @Override
    public void close() {
        // HttpClient doesn't require explicit close in modern JDK
        LOG.debug("SOAP client closed");
    }
}
