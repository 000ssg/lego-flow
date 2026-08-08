package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.*;
import ssg.legoflow.ws.content.ContentNegotiator;
import ssg.legoflow.ws.content.JsonCodec;
import ssg.legoflow.ws.content.XmlCodec;
import ssg.legoflow.ws.endpoint.AsyncEndpointInvoker;
import ssg.legoflow.ws.endpoint.Endpoint;
import ssg.legoflow.ws.endpoint.EndpointInvoker;
import ssg.legoflow.ws.request.RequestMapper;
import ssg.legoflow.ws.request.ResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive demo of all Web Services module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house components</b> — No external dependencies.
 * Runs anywhere without installation. Uses the web-services abstractions directly
 * with the HTTP module's {@link HttpRouter} for request dispatch.</p>
 *
 * <p><b>Alternative: External application server</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure
 * {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production deployment with servlet containers (Tomcat, Jetty)</li>
 *   <li>Load testing with process-level isolation</li>
 *   <li>Integration testing against production application servers</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Content negotiation — Accept/Content-Type, q-factor, codec selection</li>
 *   <li>Route dispatch — registry-based path + method routing</li>
 *   <li>REST endpoints — CRUD operations via WebService</li>
 *   <li>Async dispatch — async wrapper with CompletableFuture on virtual threads</li>
 *   <li>Error handling — 404, 405, 406, 400 status codes</li>
 *   <li>Filter chain — path, method, and content-type filters</li>
 *   <li>Response formats — JSON, XML, and plain-text output</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoWebServicesAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoWebServicesAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house web-services components (no external dependencies)
    // Alternative: set USE_EXTERNAL=true and configure host/port
    // =========================================================================

    /** Set to {@code true} to connect to an external application server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 8080;

    private DemoWebServicesAll() {}

    /**
     * Results from running the full web services demo.
     *
     * @param contentNegotiation true if content type negotiation (Accept header, q-factor) worked
     * @param routeDispatch      true if registry-based request routing/dispatch succeeded
     * @param restEndpoints      true if REST endpoint CRUD handling worked
     * @param asyncDispatch      true if async wrapper with CompletableFuture completed correctly
     * @param errorHandling      true if error/exception status codes returned correctly
     * @param filterChain        true if request/response filters applied correctly
     * @param responseFormats    true if JSON/XML/plain-text response formatting worked
     */
    public record Results(
            boolean contentNegotiation,
            boolean routeDispatch,
            boolean restEndpoints,
            boolean asyncDispatch,
            boolean errorHandling,
            boolean filterChain,
            boolean responseFormats
    ) {}

    /**
     * Runs the comprehensive demo covering all web services features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        boolean contentNeg = demoContentNegotiation();
        boolean routeDispatch = demoRouteDispatch();
        boolean restEndpoints = demoRestEndpoints();
        boolean asyncDispatch = demoAsyncDispatch();
        boolean errorHandling = demoErrorHandling();
        boolean filterChain = demoFilterChain();
        boolean responseFormats = demoResponseFormats();

        return new Results(
                contentNeg, routeDispatch, restEndpoints, asyncDispatch,
                errorHandling, filterChain, responseFormats
        );
    }

    // ======================== 1. CONTENT NEGOTIATION ========================

    /**
     * Demonstrates content negotiation: Accept header parsing with q-factors,
     * codec selection, wildcard fallback, and 406 Not Acceptable.
     */
    static boolean demoContentNegotiation() {
        LOG.info("=== 1. Content Negotiation ===");
        var negotiator = new ContentNegotiator(
                List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN));

        // JSON preferred via q-factor
        var jsonRequest = HttpRequest.of(HttpMethod.GET, "/info");
        jsonRequest.getHeaders().set(HttpHeaders.ACCEPT, "text/xml;q=0.5, application/json;q=1.0");
        var jsonResult = negotiator.negotiate(jsonRequest);
        boolean jsonPreferred = jsonResult == MediaType.APPLICATION_JSON;
        LOG.info("Q-factor negotiation: preferred={}", jsonResult);

        // XML preferred
        var xmlRequest = HttpRequest.of(HttpMethod.GET, "/info");
        xmlRequest.getHeaders().set(HttpHeaders.ACCEPT, "text/xml;q=1.0, application/json;q=0.3");
        var xmlResult = negotiator.negotiate(xmlRequest);
        boolean xmlPreferred = xmlResult == MediaType.TEXT_XML;
        LOG.info("XML preferred: {}", xmlResult);

        // Wildcard fallback
        var wildcardRequest = HttpRequest.of(HttpMethod.GET, "/info");
        wildcardRequest.getHeaders().set(HttpHeaders.ACCEPT, "*/*");
        var wildcardResult = negotiator.negotiate(wildcardRequest);
        boolean wildcardOk = wildcardResult == MediaType.APPLICATION_JSON; // first registered
        LOG.info("Wildcard fallback: {}", wildcardResult);

        // No Accept header -> default
        var noAcceptRequest = HttpRequest.of(HttpMethod.GET, "/info");
        var noAcceptResult = negotiator.negotiateOrDefault(noAcceptRequest);
        boolean defaultOk = noAcceptResult == MediaType.APPLICATION_JSON;
        LOG.info("No Accept header default: {}", noAcceptResult);

        // 406 Not Acceptable
        var unsupportedRequest = HttpRequest.of(HttpMethod.GET, "/info");
        unsupportedRequest.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var unsupportedResult = negotiator.negotiate(unsupportedRequest);
        boolean notAcceptable = unsupportedResult == null;
        LOG.info("Unsupported type -> null (406): {}", notAcceptable);

        return jsonPreferred && xmlPreferred && wildcardOk && defaultOk && notAcceptable;
    }

    // ======================== 2. ROUTE DISPATCH ==============================

    /**
     * Demonstrates registry-based route dispatch: service registration,
     * route installation, and path-based request routing.
     */
    static boolean demoRouteDispatch() {
        LOG.info("=== 2. Route Dispatch ===");
        var registry = new WebServiceRegistry();
        var router = new HttpRouter();

        // Register services
        registry.register(new HelloWorldService());
        registry.register(new EchoWebService());
        registry.installRoutes(router);

        // Dispatch to hello service
        var helloResp = router.dispatch(null, HttpRequest.of(HttpMethod.GET, "/hello"));
        boolean helloOk = helloResp.getStatus() == HttpStatus.OK
                && "Hello, World!".equals(helloResp.getBodyAsString());
        LOG.info("GET /hello: {} — {}", helloResp.getStatus(), helloResp.getBodyAsString());

        // Dispatch to echo service
        var echoReq = HttpRequest.of(HttpMethod.POST, "/echo");
        echoReq.setBody(ByteBuffer.wrap("echo-test".getBytes(StandardCharsets.UTF_8)));
        echoReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
        var echoResp = router.dispatch(null, echoReq);
        boolean echoOk = echoResp.getStatus() == HttpStatus.OK
                && "echo-test".equals(echoResp.getBodyAsString());
        LOG.info("POST /echo: {} — {}", echoResp.getStatus(), echoResp.getBodyAsString());

        // Registry lookup
        var found = registry.getService("/hello");
        boolean lookupOk = found != null;
        LOG.info("Registry lookup /hello: found={}", lookupOk);

        // List all services
        var services = registry.getServices();
        boolean listOk = services.size() == 2;
        LOG.info("Registered services: {}", services.size());

        return helloOk && echoOk && lookupOk && listOk;
    }

    // ======================== 3. REST ENDPOINTS ==============================

    /**
     * Demonstrates REST endpoint handling: CRUD operations via TodoApiService
     * with JSON request/response bodies.
     */
    static boolean demoRestEndpoints() {
        LOG.info("=== 3. REST Endpoints ===");
        var registry = new WebServiceRegistry();
        var router = new HttpRouter();
        var todoService = new TodoApiService();
        registry.register(todoService);
        registry.installRoutes(router);

        // POST — create todo
        var createReq = HttpRequest.of(HttpMethod.POST, "/todos");
        createReq.setBody(ByteBuffer.wrap("{\"title\":\"Buy milk\",\"done\":\"false\"}"
                .getBytes(StandardCharsets.UTF_8)));
        createReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        var createResp = router.dispatch(null, createReq);
        boolean created = createResp.getStatus() == HttpStatus.CREATED;
        LOG.info("POST /todos: {}", createResp.getStatus());

        // GET — list todos
        var listResp = router.dispatch(null, HttpRequest.of(HttpMethod.GET, "/todos"));
        boolean listed = listResp.getStatus() == HttpStatus.OK;
        LOG.info("GET /todos: {} — {}", listResp.getStatus(), listResp.getBodyAsString());

        // PUT — update todo
        var updateReq = HttpRequest.of(HttpMethod.PUT, "/todos");
        updateReq.setBody(ByteBuffer.wrap("{\"id\":\"1\",\"title\":\"Buy milk\",\"done\":\"true\"}"
                .getBytes(StandardCharsets.UTF_8)));
        updateReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        var updateResp = router.dispatch(null, updateReq);
        boolean updated = updateResp.getStatus() == HttpStatus.OK;
        LOG.info("PUT /todos: {}", updateResp.getStatus());

        // DELETE — remove todo
        var deleteResp = router.dispatch(null,
                HttpRequest.of(HttpMethod.DELETE, "/todos?id=1"));
        boolean deleted = deleteResp.getStatus() == HttpStatus.NO_CONTENT;
        LOG.info("DELETE /todos?id=1: {}", deleteResp.getStatus());

        // Verify empty
        boolean empty = todoService.getTodoCount() == 0;
        LOG.info("Todo count after delete: {}", todoService.getTodoCount());

        return created && listed && updated && deleted && empty;
    }

    // ======================== 4. ASYNC DISPATCH ==============================

    /**
     * Demonstrates async wrapper: AsyncWebService, AsyncWebServiceRegistry,
     * and AsyncEndpointInvoker returning CompletableFuture on virtual threads.
     */
    static boolean demoAsyncDispatch() throws Exception {
        LOG.info("=== 4. Async Dispatch ===");

        // Async WebService
        var helloService = new HelloWorldService();
        var asyncService = new AsyncWebService(helloService);
        var descriptorFuture = asyncService.getDescriptor();
        var descriptor = descriptorFuture.get();
        boolean descriptorOk = descriptor.path().equals("/hello");
        LOG.info("Async getDescriptor: path={}", descriptor.path());

        // Async handle
        var request = HttpRequest.of(HttpMethod.GET, "/hello");
        var responseFuture = asyncService.handle(null, request);
        var response = responseFuture.get();
        boolean handleOk = response.getStatus() == HttpStatus.OK
                && "Hello, World!".equals(response.getBodyAsString());
        LOG.info("Async handle: {} — {}", response.getStatus(), response.getBodyAsString());

        // Sync delegate access
        boolean syncOk = asyncService.sync() == helloService;
        LOG.info("Sync delegate access: {}", syncOk);

        // Async WebServiceRegistry
        var registry = new WebServiceRegistry();
        var asyncRegistry = new AsyncWebServiceRegistry(registry);
        asyncRegistry.register(helloService).get();
        var foundFuture = asyncRegistry.getService("/hello");
        boolean registryOk = foundFuture.get() != null;
        LOG.info("Async registry lookup: found={}", registryOk);

        var servicesFuture = asyncRegistry.getServices();
        boolean servicesOk = servicesFuture.get().size() == 1;
        LOG.info("Async getServices: count={}", servicesFuture.get().size());

        asyncRegistry.unregister("/hello").get();
        boolean unregisterOk = asyncRegistry.getService("/hello").get() == null;
        LOG.info("Async unregister: removed={}", unregisterOk);

        // Async EndpointInvoker
        var endpoints = List.of(
                new Endpoint("/test", HttpMethod.GET,
                        (ctx, req) -> HttpResponse.of(HttpStatus.OK, "async-endpoint")));
        var invoker = new EndpointInvoker(endpoints);
        var asyncInvoker = new AsyncEndpointInvoker(invoker);
        var invokeReq = HttpRequest.of(HttpMethod.GET, "/test");
        var invokeResp = asyncInvoker.invoke(null, invokeReq).get();
        boolean invokerOk = invokeResp.getStatus() == HttpStatus.OK
                && "async-endpoint".equals(invokeResp.getBodyAsString());
        LOG.info("Async invoke: {} — {}", invokeResp.getStatus(), invokeResp.getBodyAsString());

        boolean invokerSyncOk = asyncInvoker.sync() == invoker;
        LOG.info("Async invoker sync delegate: {}", invokerSyncOk);

        return descriptorOk && handleOk && syncOk && registryOk
                && servicesOk && unregisterOk && invokerOk && invokerSyncOk;
    }

    // ======================== 5. ERROR HANDLING ==============================

    /**
     * Demonstrates error handling: 404 Not Found, 405 Method Not Allowed,
     * 406 Not Acceptable, and 400 Bad Request responses.
     */
    static boolean demoErrorHandling() {
        LOG.info("=== 5. Error Handling ===");
        var registry = new WebServiceRegistry();
        var router = new HttpRouter();
        registry.register(new HelloWorldService());
        registry.register(new MultiFormatService());
        registry.installRoutes(router);

        // 404 Not Found — unknown path
        var notFoundResp = router.dispatch(null, HttpRequest.of(HttpMethod.GET, "/unknown"));
        boolean is404 = notFoundResp.getStatus() == HttpStatus.NOT_FOUND;
        LOG.info("Unknown path: {}", notFoundResp.getStatus());

        // 405 Method Not Allowed — POST to GET-only service
        var methodNotAllowed = router.dispatch(null, HttpRequest.of(HttpMethod.POST, "/hello"));
        boolean is405 = methodNotAllowed.getStatus() == HttpStatus.METHOD_NOT_ALLOWED;
        LOG.info("Wrong method: {}", methodNotAllowed.getStatus());

        // 406 Not Acceptable — unsupported Accept type
        var notAcceptReq = HttpRequest.of(HttpMethod.GET, "/info");
        notAcceptReq.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var notAcceptResp = router.dispatch(null, notAcceptReq);
        boolean is406 = notAcceptResp.getStatus() == HttpStatus.NOT_ACCEPTABLE;
        LOG.info("Unsupported Accept: {}", notAcceptResp.getStatus());

        // EndpointInvoker 404 — no matching endpoint
        var invoker = new EndpointInvoker(List.of(
                new Endpoint("/known", HttpMethod.GET,
                        (ctx, req) -> HttpResponse.of(HttpStatus.OK, "found"))));
        var epNotFound = invoker.invoke(null, HttpRequest.of(HttpMethod.GET, "/missing"));
        boolean epIs404 = epNotFound.getStatus() == HttpStatus.NOT_FOUND;
        LOG.info("Endpoint not found: {}", epNotFound.getStatus());

        // ResponseMapper error helpers
        var responseMapper = new ResponseMapper();
        var badReq = responseMapper.badRequest("Invalid input");
        boolean badReqOk = badReq.getStatus() == HttpStatus.BAD_REQUEST;
        var notFoundMsg = responseMapper.notFound("Resource missing");
        boolean notFoundOk = notFoundMsg.getStatus() == HttpStatus.NOT_FOUND;
        LOG.info("Error helpers: badRequest={}, notFound={}", badReqOk, notFoundOk);

        return is404 && is405 && is406 && epIs404 && badReqOk && notFoundOk;
    }

    // ======================== 6. FILTER CHAIN ================================

    /**
     * Demonstrates request/response filters: path filter, method filter,
     * content-type filter, and filter chaining.
     */
    static boolean demoFilterChain() {
        LOG.info("=== 6. Filter Chain ===");
        var ctx = new ssg.legoflow.blocks.DefaultContext();

        // Path filter — passes requests starting with /api
        var pathFilter = WebServiceFilter.byPath("/api");
        var apiReq = HttpRequest.of(HttpMethod.GET, "/api/users");
        var otherReq = HttpRequest.of(HttpMethod.GET, "/health");
        var pathResult = pathFilter.filter(ctx, apiReq, otherReq);
        boolean pathOk = pathResult.length == 1 && pathResult[0].getUri().equals("/api/users");
        LOG.info("Path filter /api: passed={}", pathResult.length);

        // Method filter — passes only GET requests
        var methodFilter = WebServiceFilter.byMethod(HttpMethod.GET);
        var getReq = HttpRequest.of(HttpMethod.GET, "/data");
        var postReq = HttpRequest.of(HttpMethod.POST, "/data");
        var methodResult = methodFilter.filter(ctx, getReq, postReq);
        boolean methodOk = methodResult.length == 1 && methodResult[0].getMethod() == HttpMethod.GET;
        LOG.info("Method filter GET: passed={}", methodResult.length);

        // Content-type filter — passes only JSON requests
        var ctFilter = WebServiceFilter.byContentType("application/json");
        var jsonReq = HttpRequest.of(HttpMethod.POST, "/data");
        jsonReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        var xmlReq = HttpRequest.of(HttpMethod.POST, "/data");
        xmlReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/xml");
        var ctResult = ctFilter.filter(ctx, jsonReq, xmlReq);
        boolean ctOk = ctResult.length == 1;
        LOG.info("Content-type filter JSON: passed={}", ctResult.length);

        // Filter chaining — path + method combined
        var chain1 = pathFilter.filter(ctx, apiReq, otherReq);
        var chain2 = methodFilter.filter(ctx, chain1);
        boolean chainOk = chain2.length == 1 && chain2[0].getUri().equals("/api/users");
        LOG.info("Filter chain (path + method): passed={}", chain2.length);

        return pathOk && methodOk && ctOk && chainOk;
    }

    // ======================== 7. RESPONSE FORMATS ============================

    /**
     * Demonstrates response formatting: JSON, XML, and plain-text output
     * from the same service endpoint via content negotiation.
     */
    static boolean demoResponseFormats() {
        LOG.info("=== 7. Response Formats ===");
        var registry = new WebServiceRegistry();
        var router = new HttpRouter();
        registry.register(new MultiFormatService());
        registry.installRoutes(router);

        // JSON response
        var jsonReq = HttpRequest.of(HttpMethod.GET, "/info");
        jsonReq.getHeaders().set(HttpHeaders.ACCEPT, "application/json");
        var jsonResp = router.dispatch(null, jsonReq);
        boolean jsonOk = jsonResp.getStatus() == HttpStatus.OK
                && jsonResp.getHeaders().get(HttpHeaders.CONTENT_TYPE).contains("application/json");
        var jsonBody = jsonResp.getBodyAsString();
        boolean jsonContent = jsonBody.contains("\"service\"") && jsonBody.contains("\"MultiFormat\"");
        LOG.info("JSON response: {} — {}", jsonResp.getStatus(), jsonBody);

        // XML response
        var xmlReq = HttpRequest.of(HttpMethod.GET, "/info");
        xmlReq.getHeaders().set(HttpHeaders.ACCEPT, "text/xml");
        var xmlResp = router.dispatch(null, xmlReq);
        boolean xmlOk = xmlResp.getStatus() == HttpStatus.OK
                && xmlResp.getHeaders().get(HttpHeaders.CONTENT_TYPE).contains("text/xml");
        var xmlBody = xmlResp.getBodyAsString();
        boolean xmlContent = xmlBody.contains("<service>") && xmlBody.contains("MultiFormat");
        LOG.info("XML response: {} — {}", xmlResp.getStatus(), xmlBody);

        // Plain text response
        var textReq = HttpRequest.of(HttpMethod.GET, "/info");
        textReq.getHeaders().set(HttpHeaders.ACCEPT, "text/plain");
        var textResp = router.dispatch(null, textReq);
        boolean textOk = textResp.getStatus() == HttpStatus.OK
                && textResp.getHeaders().get(HttpHeaders.CONTENT_TYPE).contains("text/plain");
        var textBody = textResp.getBodyAsString();
        boolean textContent = textBody.contains("service=MultiFormat");
        LOG.info("Text response: {} — {}", textResp.getStatus(), textBody);

        // Codecs directly
        var jsonCodec = new JsonCodec();
        var data = new LinkedHashMap<String, String>();
        data.put("key", "value");
        var encoded = jsonCodec.encode(data);
        var decoded = jsonCodec.decode(encoded);
        boolean codecRoundTrip = "value".equals(decoded.get("key"));
        LOG.info("JSON codec round-trip: {}", codecRoundTrip);

        var xmlCodec = new XmlCodec();
        var xmlEncoded = xmlCodec.encode("root", data);
        var xmlDecoded = xmlCodec.decode(xmlEncoded);
        boolean xmlRoundTrip = "value".equals(xmlDecoded.get("key"));
        LOG.info("XML codec round-trip: {}", xmlRoundTrip);

        return jsonOk && jsonContent && xmlOk && xmlContent
                && textOk && textContent && codecRoundTrip && xmlRoundTrip;
    }
}
