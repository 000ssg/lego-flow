package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyFilterTest {

    @Test
    void testFilterRequestIdentity() {
        ProxyFilter filter = new IdentityFilter();
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        var filtered = filter.filterRequest(request);
        assertThat(filtered).isSameAs(request);
    }

    @Test
    void testFilterResponseIdentity() {
        ProxyFilter filter = new IdentityFilter();
        var response = HttpResponse.of(HttpStatus.OK, "body");
        var filtered = filter.filterResponse(response);
        assertThat(filtered).isSameAs(response);
    }

    @Test
    void testFilterName() {
        ProxyFilter filter = new IdentityFilter();
        assertThat(filter.getName()).isEqualTo("identity");
    }

    @Test
    void testFilterDefaultOrder() {
        ProxyFilter filter = new IdentityFilter();
        assertThat(filter.getOrder()).isEqualTo(0);
    }

    @Test
    void testFilterCustomOrder() {
        ProxyFilter filter = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) { return request; }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "custom"; }
            @Override
            public int getOrder() { return 10; }
        };
        assertThat(filter.getOrder()).isEqualTo(10);
    }

    @Test
    void testHeaderAddingFilter() {
        ProxyFilter filter = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                request.getHeaders().set("x-custom", "added");
                return request;
            }
            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                response.getHeaders().set("x-filtered", "true");
                return response;
            }
            @Override
            public String getName() { return "header-adder"; }
        };

        var request = HttpRequest.of(HttpMethod.GET, "/test");
        var filteredReq = filter.filterRequest(request);
        assertThat(filteredReq.getHeaders().get("x-custom")).isEqualTo("added");

        var response = HttpResponse.of(HttpStatus.OK);
        var filteredResp = filter.filterResponse(response);
        assertThat(filteredResp.getHeaders().get("x-filtered")).isEqualTo("true");
    }

    @Test
    void testFilterChaining() {
        ProxyFilter filter1 = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                request.getHeaders().set("x-step", "1");
                return request;
            }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "step1"; }
            @Override
            public int getOrder() { return 1; }
        };
        ProxyFilter filter2 = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                String prev = request.getHeaders().get("x-step");
                request.getHeaders().set("x-step", prev + ",2");
                return request;
            }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "step2"; }
            @Override
            public int getOrder() { return 2; }
        };

        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request = filter1.filterRequest(request);
        request = filter2.filterRequest(request);
        assertThat(request.getHeaders().get("x-step")).isEqualTo("1,2");
    }

    @Test
    void testFilterRequestModifiesUri() {
        ProxyFilter filter = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                return new HttpRequest(request.getMethod(), "/rewritten",
                        request.getVersion(), request.getHeaders());
            }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "rewriter"; }
        };

        var request = HttpRequest.of(HttpMethod.GET, "/original");
        var filtered = filter.filterRequest(request);
        assertThat(filtered.getUri()).isEqualTo("/rewritten");
    }

    @Test
    void testFilterResponseModifiesStatus() {
        ProxyFilter filter = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) { return request; }
            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                if (response.getStatus() == HttpStatus.OK) {
                    return HttpResponse.of(HttpStatus.ACCEPTED, "modified");
                }
                return response;
            }
            @Override
            public String getName() { return "status-modifier"; }
        };

        var response = HttpResponse.of(HttpStatus.OK, "original");
        var filtered = filter.filterResponse(response);
        assertThat(filtered.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void testMultipleFiltersOrdering() {
        ProxyFilter low = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) { return request; }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "low"; }
            @Override
            public int getOrder() { return -5; }
        };
        ProxyFilter high = new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) { return request; }
            @Override
            public HttpResponse filterResponse(HttpResponse response) { return response; }
            @Override
            public String getName() { return "high"; }
            @Override
            public int getOrder() { return 100; }
        };

        assertThat(low.getOrder()).isLessThan(high.getOrder());
    }

    private static class IdentityFilter implements ProxyFilter {
        @Override
        public HttpRequest filterRequest(HttpRequest request) { return request; }
        @Override
        public HttpResponse filterResponse(HttpResponse response) { return response; }
        @Override
        public String getName() { return "identity"; }
    }
}
