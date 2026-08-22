package ssg.legoflow.http.server;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.transfer.ContentEncodingCodec;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;
import java.nio.ByteBuffer;
public class HttpServer extends AbstractService<HttpRequest, HttpResponse> {

    private final ServerConfig config;
    private final HttpRouter router;
    private boolean compressionEnabled = true;

    public HttpServer(ServerConfig config) {
        this("http-server", config);
    }

    public HttpServer(String name, ServerConfig config) {
        super(HttpRequest.class, HttpResponse.class,
                new ServiceDescriptor(name, "HTTP Server on port " + config.getPort()));
        this.config = config;
        this.router = new HttpRouter();
    }

    public HttpRouter getRouter() {
        return router;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public void setCompressionEnabled(boolean enabled) {
        this.compressionEnabled = enabled;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpResponse[] convertToOutput(Context ctx, HttpRequest... input) {
        var responses = new HttpResponse[input.length];
        for (int i = 0; i < input.length; i++) {
            try {
                var httpCtx = createHttpContext(ctx, input[i]);
                responses[i] = router.dispatch(httpCtx, input[i]);
                if (compressionEnabled) {
                    responses[i] = applyCompression(input[i], responses[i], ctx);
                }
            } catch (Exception e) {
                ctx.handleError(e);
                responses[i] = HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
            }
        }
        return responses;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpRequest[] convertToInput(Context ctx, HttpResponse... output) {
        return new HttpRequest[0];
    }

    public HttpResponse handleRequest(Context ctx, HttpRequest request) {
        var httpCtx = createHttpContext(ctx, request);
        var response = router.dispatch(httpCtx, request);
        if (compressionEnabled) {
            response = applyCompression(request, response, ctx);
        }
        return response;
    }

    private HttpResponse applyCompression(HttpRequest request, HttpResponse response, Context ctx) {
        response.getHeaders().set(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);

        var body = response.getBody();
        if (body == null || body.remaining() == 0) return response;

        var acceptEncoding = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        if (acceptEncoding == null || !acceptEncoding.toLowerCase().contains("gzip")) return response;

        var alreadyEncoded = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        if (alreadyEncoded != null) return response;

        var codec = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.COMPRESS);
        ByteBuffer[] compressed = codec.filter(ctx, body);
        if (compressed.length > 0) {
            response.setBody(compressed[0]);
            response.getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            response.getHeaders().set(HttpHeaders.CONTENT_LENGTH,
                    String.valueOf(compressed[0].remaining()));
        }
        return response;
    }

    private HttpContext createHttpContext(Context ctx, HttpRequest request) {
        return new SimpleHttpContext(ctx, request);
    }

    private static class SimpleHttpContext implements HttpContext {
        private final Context delegate;
        private final HttpRequest request;
        private HttpResponse response;

        SimpleHttpContext(Context delegate, HttpRequest request) {
            this.delegate = delegate;
            this.request = request;
        }

        @Override public HttpRequest getRequest() { return request; }
        @Override public HttpResponse getResponse() { return response; }
        @Override public void setResponse(HttpResponse response) { this.response = response; }
        @Override public org.slf4j.Logger getLogger() { return delegate.getLogger(); }
        @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() { return delegate.getStatistics(); }
        @Override public void handleError(Throwable error) { delegate.handleError(error); }
        @Override public <T> T getAttribute(String key) { return delegate.getAttribute(key); }
        @Override public void setAttribute(String key, Object value) { delegate.setAttribute(key, value); }
        @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return null; }
        @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return null; }
        @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return null; }
        @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return null; }
        @Override public ssg.legoflow.service.user.ServiceUser getUser() { return ssg.legoflow.service.user.ServiceUser.anonymous(); }
        @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return false; }
        @Override public void checkPermission(String operation) {}
    }
}
