package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.core.HttpProtocolCodec;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http.security.SslConfig;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.upnp.controlpoint.ControlPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main web server for the Media Control Center demo application.
 *
 * <p>Creates an {@link HttpServer} with an {@link HttpRouter} configured
 * with REST API routes and serves a React single-page application.
 * Binds to a TCP port and accepts connections using virtual threads.
 *
 * <p>Default ports: 8080 (HTTP), 8443 (HTTPS).
 *
 * @since 0.1.0
 */
public class MccWebServer {

    private static final Logger LOG = LoggerFactory.getLogger(MccWebServer.class);
    private static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1MB max request size

    private final HttpServer httpServer;
    private final HttpProtocolCodec codec;
    private final MccApiRouter apiRouter;
    private final int port;
    private final DefaultContext context;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private Thread acceptThread;

    /**
     * Creates a new web server on the specified port.
     *
     * @param port         the HTTP port to listen on
     * @param controlPoint the UPnP control point
     * @since 0.1.0
     */
    public MccWebServer(int port, ControlPoint controlPoint) {
        this(port, controlPoint, null);
    }

    /**
     * Creates a new web server with optional SSL.
     *
     * @param port         the HTTP/HTTPS port to listen on
     * @param controlPoint the UPnP control point
     * @param sslConfig    the SSL configuration, or null for plain HTTP
     * @since 0.1.0
     */
    public MccWebServer(int port, ControlPoint controlPoint, SslConfig sslConfig) {
        Objects.requireNonNull(controlPoint, "controlPoint must not be null");
        this.port = port;
        this.context = new DefaultContext();
        this.codec = new HttpProtocolCodec();

        var config = new ServerConfig(new HttpFeatureSet("mcc-web"));
        config.setPort(port);
        if (sslConfig != null) {
            config.setSslConfig(sslConfig);
        }

        this.httpServer = new HttpServer("mcc-web-server", config);
        this.apiRouter = new MccApiRouter(controlPoint);

        // Configure routes on the HTTP router
        HttpRouter router = httpServer.getRouter();
        apiRouter.configureRoutes(router);

        // Disable gzip compression for simplicity in this demo
        httpServer.setCompressionEnabled(false);
    }

    /**
     * Starts the web server, binding to the configured port and
     * accepting connections on virtual threads.
     *
     * @since 0.1.0
     */
    public void start() {
        if (running) return;
        running = true;

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            LOG.info("MCC Web Server listening on port {}", port);
        } catch (IOException e) {
            running = false;
            LOG.error("Failed to bind MCC Web Server to port {}: {}", port, e.getMessage());
            throw new RuntimeException("Failed to start web server on port " + port, e);
        }

        // Use virtual thread executor for handling connections
        acceptExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Accept loop on a dedicated virtual thread
        acceptThread = Thread.ofVirtual().name("mcc-web-accept").start(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    var clientSocket = serverSocket.accept();
                    acceptExecutor.submit(() -> handleConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        LOG.debug("Accept error (server may be shutting down): {}", e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Stops the web server and releases resources.
     *
     * @since 0.1.0
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing server socket: {}", e.getMessage());
        }
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
        }
        apiRouter.close();
        LOG.info("MCC Web Server stopped");
    }

    /**
     * Handles a single client connection: reads the HTTP request,
     * dispatches it through the router, and writes the response.
     */
    private void handleConnection(java.net.Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(30_000); // 30 second read timeout
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            // Read request bytes (simple approach: read until headers end, then body)
            byte[] requestBytes = readHttpRequest(in);
            if (requestBytes == null || requestBytes.length == 0) {
                return; // Client closed connection
            }

            // Parse HTTP request
            HttpRequest request;
            try {
                request = codec.parseRequest(ByteBuffer.wrap(requestBytes));
            } catch (Exception e) {
                LOG.warn("Failed to parse HTTP request: {}", e.getMessage(), e);
                writeErrorResponse(out, HttpStatus.BAD_REQUEST, "Bad Request");
                closeQuietly(clientSocket);
                return;
            }

            LOG.info("Request: {} {} (headers: {})", request.getMethod(),
                    request.getUri().length() > 120 ? request.getUri().substring(0, 120) + "..." : request.getUri(),
                    request.getHeaders().names());

            // Dispatch through router
            HttpResponse response;
            try {
                response = httpServer.handleRequest(context, request);
                MccApiRouter.addCorsHeaders(response);
            } catch (Exception e) {
                LOG.error("Request handler error for {} {}: {}", request.getMethod(),
                        request.getUri(), e.getMessage(), e);
                response = HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error: " + e.getMessage());
            }

            // Add Connection: close to simplify (no keep-alive for demo)
            response.getHeaders().set("Connection", "close");

            LOG.info("Response: {} {} stream={} headers={}",
                    response.getStatus().code(), response.getStatus().reason(),
                    response.hasStreamBody() ? response.getBodyStreamLength() + " bytes" : "no",
                    response.getHeaders().names());

            if (response.hasStreamBody()) {
                // Streaming response: write headers first, then pipe body stream
                long streamLen = response.getBodyStreamLength();
                if (streamLen >= 0) {
                    response.getHeaders().set("Content-Length", String.valueOf(streamLen));
                }
                byte[] headerBytes = codec.serializeResponseHeaders(response);
                out.write(headerBytes);
                out.flush(); // flush headers immediately so browser starts processing
                try (var bodyStream = response.getBodyStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = bodyStream.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
                out.flush();
            } else {
                // Buffered response: serialize headers + body together
                var body = response.getBody();
                if (body != null && body.remaining() > 0) {
                    response.getHeaders().set("Content-Length",
                            String.valueOf(body.remaining()));
                } else {
                    response.getHeaders().set("Content-Length", "0");
                }
                ByteBuffer responseBuffer = codec.serializeResponse(response);
                byte[] responseBytes = new byte[responseBuffer.remaining()];
                responseBuffer.get(responseBytes);
                out.write(responseBytes);
                out.flush();
            }

        } catch (IOException e) {
            LOG.warn("Connection handling error: {}", e.getMessage());
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // This typically means stale class files: a module was changed but
            // dependent modules weren't recompiled. Log at ERROR level to help diagnose.
            LOG.error("Class compatibility error handling request (rebuild with "
                    + "'mvn clean compile' from root): {}", e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Unexpected error handling connection: {}", e.getMessage(), e);
        } finally {
            closeQuietly(clientSocket);
        }
    }

    /**
     * Closes a socket without throwing exceptions.
     */
    private static void closeQuietly(java.net.Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Reads an HTTP request from the input stream by first reading headers
     * (until CRLFCRLF), then reading the body based on Content-Length.
     */
    private byte[] readHttpRequest(InputStream in) throws IOException {
        var buffer = new java.io.ByteArrayOutputStream(4096);
        int headerEnd = -1;
        int b;

        // Read until we find \r\n\r\n (header end) or stream ends
        while ((b = in.read()) != -1) {
            buffer.write(b);
            int size = buffer.size();
            if (size >= 4) {
                byte[] bytes = buffer.toByteArray();
                if (bytes[size - 4] == '\r' && bytes[size - 3] == '\n'
                        && bytes[size - 2] == '\r' && bytes[size - 1] == '\n') {
                    headerEnd = size - 4;
                    break;
                }
            }
            if (size > MAX_REQUEST_SIZE) {
                LOG.warn("Request headers exceed maximum size");
                return null;
            }
        }

        if (headerEnd < 0) {
            // Stream ended before headers completed
            return buffer.size() > 0 ? buffer.toByteArray() : null;
        }

        // Check for Content-Length to read body
        String headerSection = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        int contentLength = 0;
        for (String line : headerSection.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                } catch (NumberFormatException ignored) {
                    // Skip malformed content-length
                }
                break;
            }
        }

        // Read body if present
        if (contentLength > 0) {
            if (contentLength > MAX_REQUEST_SIZE) {
                LOG.warn("Request body exceeds maximum size: {}", contentLength);
                return null;
            }
            byte[] bodyBytes = in.readNBytes(contentLength);
            buffer.write(bodyBytes);
        }

        return buffer.toByteArray();
    }

    /**
     * Writes a simple error response directly to the output stream.
     */
    private void writeErrorResponse(OutputStream out, HttpStatus status, String message)
            throws IOException {
        var response = HttpResponse.of(status, message);
        response.getHeaders().set("Content-Length", String.valueOf(message.getBytes(StandardCharsets.UTF_8).length));
        response.getHeaders().set("Connection", "close");
        ByteBuffer buf = codec.serializeResponse(response);
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        out.write(bytes);
        out.flush();
    }

    /**
     * Returns the port this server is listening on.
     *
     * <p>If the server was started with port 0 (auto-assign), this returns
     * the actual assigned port after {@link #start()} has been called.
     *
     * @return the server port
     * @since 0.1.0
     */
    public int getPort() {
        if (serverSocket != null && serverSocket.isBound()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the underlying HTTP server instance.
     *
     * @return the HTTP server
     * @since 0.1.0
     */
    public HttpServer getHttpServer() {
        return httpServer;
    }

    /**
     * Returns the API router for accessing handlers.
     *
     * @return the API router
     * @since 0.1.0
     */
    public MccApiRouter getApiRouter() {
        return apiRouter;
    }

    /**
     * Handles an HTTP request by dispatching through the router.
     *
     * <p>This is the programmatic entry point for request processing,
     * used in tests. For real HTTP traffic, the TCP accept loop
     * handles this automatically.
     *
     * @param request the HTTP request
     * @return the HTTP response
     * @since 0.1.0
     */
    public HttpResponse handleRequest(HttpRequest request) {
        if (!running) {
            return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server not running");
        }
        HttpResponse response = httpServer.handleRequest(context, request);
        MccApiRouter.addCorsHeaders(response);
        return response;
    }
}
