package ssg.legoflow.ws;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebServiceFilterTest {

    @Test
    void testFilterByPath() {
        var filter = WebServiceFilter.byPath("/api");
        var ctx = new DefaultContext();
        var req1 = HttpRequest.of(HttpMethod.GET, "/api/users");
        var req2 = HttpRequest.of(HttpMethod.GET, "/other");
        var result = filter.filter(ctx, req1, req2);
        assertThat(result).hasSize(1);
        assertThat(result[0].getUri()).isEqualTo("/api/users");
    }

    @Test
    void testFilterByMethod() {
        var filter = WebServiceFilter.byMethod(HttpMethod.POST);
        var ctx = new DefaultContext();
        var req1 = HttpRequest.of(HttpMethod.GET, "/test");
        var req2 = HttpRequest.of(HttpMethod.POST, "/test");
        var result = filter.filter(ctx, req1, req2);
        assertThat(result).hasSize(1);
        assertThat(result[0].getMethod()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void testFilterByContentType() {
        var filter = WebServiceFilter.byContentType("application/json");
        var ctx = new DefaultContext();
        var req = HttpRequest.of(HttpMethod.POST, "/test");
        req.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        var result = filter.filter(ctx, req);
        assertThat(result).hasSize(1);
    }

    @Test
    void testFilterAllRejected() {
        var filter = WebServiceFilter.byPath("/missing");
        var ctx = new DefaultContext();
        var req = HttpRequest.of(HttpMethod.GET, "/other");
        var result = filter.filter(ctx, req);
        assertThat(result).isEmpty();
    }
}
