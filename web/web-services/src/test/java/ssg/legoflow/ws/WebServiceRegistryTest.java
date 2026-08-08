package ssg.legoflow.ws;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebServiceRegistry} and its DefaultWebServiceContext.
 *
 * @since 0.1.0
 */
class WebServiceRegistryTest {

    private final WebService testService = new WebService() {
        private final WebServiceDescriptor desc = new WebServiceDescriptor("/test", Set.of(HttpMethod.GET, HttpMethod.POST));

        @Override public WebServiceDescriptor getDescriptor() { return desc; }
        @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
            var queryParam = ctx.getQueryParameter("q");
            return HttpResponse.of(HttpStatus.OK, "handled:method=" + request.getMethod() + ",queryParam=" + queryParam);
        }
    };

    private final WebService jsonService = new WebService() {
        private final WebServiceDescriptor desc = new WebServiceDescriptor("/api/json", Set.of(HttpMethod.GET));

        @Override public WebServiceDescriptor getDescriptor() { return desc; }
        @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
            var response = HttpResponse.of(HttpStatus.OK, "{}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        }
    };

    /**
     * Mock HttpContext with proper attribute storage and request handling.
     */
    private static class TestHttpContext implements HttpContext {
        private HttpRequest request;
        private final Map<String, Object> attributes = new HashMap<>();
        
        @Override public HttpRequest getRequest() { return request; }
        @Override public HttpResponse getResponse() { return null; }
        @Override public void setResponse(HttpResponse response) {}
        @Override public org.slf4j.Logger getLogger() { return org.slf4j.LoggerFactory.getLogger(WebServiceRegistryTest.class); }
        @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() { return null; }
        @Override public void handleError(Throwable error) {}
        @Override public <T> T getAttribute(String key) { return (T) attributes.get(key); }
        @Override public void setAttribute(String key, Object value) { attributes.put(key, value); }
        @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return null; }
        @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return null; }
        @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return null; }
        @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return null; }
        @Override public ssg.legoflow.service.user.ServiceUser getUser() { return ssg.legoflow.service.user.ServiceUser.anonymous(); }
        @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return false; }
        @Override public void checkPermission(String operation) {}
    }

    private WebServiceRegistry registry;
    private HttpRouter router;

    private TestHttpContext createContext() {
        return new TestHttpContext();
    }

    @Test
    void testRegisterAndGetService() {
        var registry = new WebServiceRegistry();
        registry.register(testService);
        var service = registry.getService("/test");
        assertThat(service).isSameAs(testService);
    }

    @Test
    void testUnregister() {
        var registry = new WebServiceRegistry();
        registry.register(testService);
        registry.unregister("/test");
        assertThat(registry.getService("/test")).isNull();
    }

    @Test
    void testGetServices() {
        var registry = new WebServiceRegistry();
        registry.register(testService);
        registry.register(jsonService);
        var services = registry.getServices();
        assertThat(services).hasSize(2);
    }

    @Test
    void testInstallRoutesAndDispatch() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        registry.register(testService);
        registry.installRoutes(router);

        var ctx = createContext();
        var request = HttpRequest.of(HttpMethod.GET, "/test?foo=bar");
        ctx.request = request;
        var response = router.dispatch(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("method=GET");
    }

    @Test
    void testInstallRoutesHandlesMultipleMethods() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        registry.register(testService); // GET and POST
        registry.installRoutes(router);

        var ctx1 = createContext();
        var getReq = HttpRequest.of(HttpMethod.GET, "/test");
        ctx1.request = getReq;
        var getResponse = router.dispatch(ctx1, getReq);

        var ctx2 = createContext();
        var postReq = HttpRequest.of(HttpMethod.POST, "/test");
        ctx2.request = postReq;
        var postResponse = router.dispatch(ctx2, postReq);

        assertThat(getResponse.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(postResponse.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testInstallRoutesNotFoundForMissingPath() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        registry.register(testService);
        registry.installRoutes(router);

        var ctx = createContext();
        var request = HttpRequest.of(HttpMethod.GET, "/nonexistent");
        var response = router.dispatch(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testInstallRoutesMethodNotAllowed() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        registry.register(jsonService); // only GET
        registry.installRoutes(router);

        var ctx = createContext();
        var request = HttpRequest.of(HttpMethod.POST, "/api/json");
        var response = router.dispatch(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void testQueryParameterExtraction() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        registry.register(testService);
        registry.installRoutes(router);

        var ctx = createContext();
        var request = HttpRequest.of(HttpMethod.GET, "/test?q=hello");
        ctx.request = request;
        var response = router.dispatch(ctx, request);
        assertThat(response.getBodyAsString()).contains("queryParam=hello");
    }

    @Test
    void testGetServiceReturnsNullForUnknownPath() {
        var registry = new WebServiceRegistry();
        assertThat(registry.getService("/unknown")).isNull();
    }

    @Test
    void testGetServicesReturnsEmptyListWhenNoServices() {
        var registry = new WebServiceRegistry();
        assertThat(registry.getServices()).isEmpty();
    }

    @Test
    void testRegisterOverwritesExistingService() {
        var registry = new WebServiceRegistry();
        registry.register(testService);
        var newService = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/test", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                return HttpResponse.of(HttpStatus.OK, "new");
            }
        };
        registry.register(newService);
        var service = registry.getService("/test");
        assertThat(service).isSameAs(newService);
    }

    @Test
    void testInstallRoutesWithEmptyRegistry() {
        var registry = new WebServiceRegistry();
        var router = new HttpRouter();
        registry.installRoutes(router);
        var ctx = createContext();
        var request = HttpRequest.of(HttpMethod.GET, "/anything");
        var response = router.dispatch(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ======================== DefaultWebServiceContext coverage ========================

    @Test
    void testDefaultContextGetRequest() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var req = ctx.getRequest();
                var resp = ctx.getResponse();
                return HttpResponse.of(HttpStatus.OK, "req=" + (req != null ? req.getUri() : "null") + ",resp=" + (resp == null));
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("req=/ctx");
    }

    @Test
    void testDefaultContextSetResponse() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx2", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var newResp = HttpResponse.of(HttpStatus.CREATED, "created");
                ctx.setResponse(newResp);
                return HttpResponse.of(HttpStatus.OK);
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx2");
        ctxHolder.request = request;
        router.dispatch(ctxHolder, request);
    }

    @Test
    void testDefaultContextAttributes() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx3", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                ctx.setAttribute("key1", "value1");
                var val = ctx.getAttribute("key1");
                var descriptor = ctx.getServiceDescriptor();
                return HttpResponse.of(HttpStatus.OK, "val=" + val + ",desc=" + (descriptor != null));
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx3");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("val=value1");
    }

    @Test
    void testDefaultContextUserAndRoles() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx4", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var user = ctx.getUser();
                var hasRole = ctx.hasRole(ssg.legoflow.service.user.ServiceRole.ADMIN);
                ctx.checkPermission("test-op");
                return HttpResponse.of(HttpStatus.OK, "user=" + (user != null) + ",admin=" + hasRole);
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx4");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("user=true");
    }

    @Test
    void testDefaultContextScopes() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx5", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var siteScope = ctx.getSiteScope();
                var appScope = ctx.getApplicationScope();
                var sessionScope = ctx.getSessionScope();
                var reqScope = ctx.getRequestScope();
                var stats = ctx.getStatistics();
                return HttpResponse.of(HttpStatus.OK, "scopes=" + (siteScope == null) + "," + (appScope == null));
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx5");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("scopes=true");
    }

    @Test
    void testDefaultContextHandleError() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx6", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                ctx.handleError(new RuntimeException("test error"));
                return HttpResponse.of(HttpStatus.OK);
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx6");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testDefaultContextGetLogger() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx7", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var logger = ctx.getLogger();
                return HttpResponse.of(HttpStatus.OK, "logger=" + (logger != null));
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx7");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("logger=true");
    }

    @Test
    void testDefaultContextGetQueryParameterNoRequest() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        // Don't set request - getQueryParameter should return null
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx8", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var qp = ctx.getQueryParameter("nonexistent");
                return HttpResponse.of(HttpStatus.OK, "qp=" + qp);
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx8");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("qp=null");
    }

    @Test
    void testDefaultContextGetPathParameterReturnsNull() {
        registry = new WebServiceRegistry();
        router = new HttpRouter();
        var ctxHolder = createContext();
        var serviceWithCtx = new WebService() {
            private final WebServiceDescriptor desc = new WebServiceDescriptor("/ctx9", Set.of(HttpMethod.GET));
            @Override public WebServiceDescriptor getDescriptor() { return desc; }
            @Override public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
                var pp = ctx.getPathParameter("any");
                return HttpResponse.of(HttpStatus.OK, "pp=" + pp); // always null per implementation
            }
        };
        registry.register(serviceWithCtx);
        registry.installRoutes(router);
        var request = HttpRequest.of(HttpMethod.GET, "/ctx9");
        ctxHolder.request = request;
        var response = router.dispatch(ctxHolder, request);
        assertThat(response.getBodyAsString()).contains("pp=null");
    }
}
