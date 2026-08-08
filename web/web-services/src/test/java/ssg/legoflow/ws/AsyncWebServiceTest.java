package ssg.legoflow.ws;

import ssg.legoflow.http.core.*;
import ssg.legoflow.ws.endpoint.AsyncEndpointInvoker;
import ssg.legoflow.ws.endpoint.Endpoint;
import ssg.legoflow.ws.endpoint.EndpointInvoker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for async wrappers: {@link AsyncWebService},
 * {@link AsyncWebServiceRegistry}, and {@link AsyncEndpointInvoker}.
 *
 * @since 0.1.0
 */
class AsyncWebServiceTest {

    private final WebService helloService = new WebService() {
        private final WebServiceDescriptor desc = new WebServiceDescriptor("/hello", Set.of(HttpMethod.GET));

        @Override
        public WebServiceDescriptor getDescriptor() { return desc; }

        @Override
        public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
            return HttpResponse.of(HttpStatus.OK, "Hello, Async!");
        }
    };

    // ======================== AsyncWebService ========================

    @Test
    void testAsyncHandle() throws Exception {
        var async = new AsyncWebService(helloService);
        var request = HttpRequest.of(HttpMethod.GET, "/hello");
        var response = async.handle(null, request).get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello, Async!");
    }

    @Test
    void testAsyncGetDescriptor() throws Exception {
        var async = new AsyncWebService(helloService);
        var descriptor = async.getDescriptor().get();

        assertThat(descriptor.path()).isEqualTo("/hello");
        assertThat(descriptor.methods()).containsExactly(HttpMethod.GET);
    }

    @Test
    void testAsyncSyncDelegate() {
        var async = new AsyncWebService(helloService);
        assertThat(async.sync()).isSameAs(helloService);
    }

    // ======================== AsyncWebServiceRegistry ========================

    @Test
    void testAsyncRegistryRegisterAndLookup() throws Exception {
        var registry = new WebServiceRegistry();
        var asyncRegistry = new AsyncWebServiceRegistry(registry);

        asyncRegistry.register(helloService).get();

        var found = asyncRegistry.getService("/hello").get();
        assertThat(found).isNotNull();
        assertThat(found).isSameAs(helloService);
    }

    @Test
    void testAsyncRegistryUnregister() throws Exception {
        var registry = new WebServiceRegistry();
        var asyncRegistry = new AsyncWebServiceRegistry(registry);

        asyncRegistry.register(helloService).get();
        asyncRegistry.unregister("/hello").get();

        var found = asyncRegistry.getService("/hello").get();
        assertThat(found).isNull();
    }

    @Test
    void testAsyncRegistryGetServices() throws Exception {
        var registry = new WebServiceRegistry();
        var asyncRegistry = new AsyncWebServiceRegistry(registry);

        asyncRegistry.register(helloService).get();

        var services = asyncRegistry.getServices().get();
        assertThat(services).hasSize(1);
    }

    @Test
    void testAsyncRegistrySyncDelegate() {
        var registry = new WebServiceRegistry();
        var asyncRegistry = new AsyncWebServiceRegistry(registry);
        assertThat(asyncRegistry.sync()).isSameAs(registry);
    }

    // ======================== AsyncEndpointInvoker ========================

    @Test
    void testAsyncEndpointInvoke() throws Exception {
        var endpoints = List.of(
                new Endpoint("/test", HttpMethod.GET,
                        (ctx, req) -> HttpResponse.of(HttpStatus.OK, "async-result")));
        var invoker = new EndpointInvoker(endpoints);
        var asyncInvoker = new AsyncEndpointInvoker(invoker);

        var request = HttpRequest.of(HttpMethod.GET, "/test");
        var response = asyncInvoker.invoke(null, request).get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("async-result");
    }

    @Test
    void testAsyncEndpointInvokeNotFound() throws Exception {
        var invoker = new EndpointInvoker(List.of());
        var asyncInvoker = new AsyncEndpointInvoker(invoker);

        var request = HttpRequest.of(HttpMethod.GET, "/missing");
        var response = asyncInvoker.invoke(null, request).get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testAsyncEndpointInvokerSyncDelegate() {
        var invoker = new EndpointInvoker(List.of());
        var asyncInvoker = new AsyncEndpointInvoker(invoker);
        assertThat(asyncInvoker.sync()).isSameAs(invoker);
    }

    // ======================== Constructor with custom executor ========================

    @Test
    void testAsyncWebServiceWithCustomExecutor() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var async = new AsyncWebService(helloService, executor);
            assertThat(async.sync()).isSameAs(helloService);
            // verify it works
            var request = HttpRequest.of(HttpMethod.GET, "/hello");
            var response = async.handle(null, request).get();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAsyncRegistryWithCustomExecutor() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var registry = new WebServiceRegistry();
        try {
            var asyncRegistry = new AsyncWebServiceRegistry(registry, executor);
            assertThat(asyncRegistry.sync()).isSameAs(registry);
            asyncRegistry.register(helloService).get();
            assertThat(asyncRegistry.getService("/hello").get()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAsyncEndpointInvokerWithCustomExecutor() throws Exception {
        var endpoints = List.of(
                new Endpoint("/custom", HttpMethod.GET,
                        (ctx, req) -> HttpResponse.of(HttpStatus.OK, "custom-exec"))
        );
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var invoker = new EndpointInvoker(endpoints);
            var asyncInvoker = new AsyncEndpointInvoker(invoker, executor);
            assertThat(asyncInvoker.sync()).isSameAs(invoker);
            var request = HttpRequest.of(HttpMethod.GET, "/custom");
            var response = asyncInvoker.invoke(null, request).get();
            assertThat(response.getBodyAsString()).isEqualTo("custom-exec");
        } finally {
            executor.shutdownNow();
        }
    }
}
