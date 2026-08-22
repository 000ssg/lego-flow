package ssg.legoflow.ws.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.ws.WebServiceFilter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WebServiceFilterDemoTest {

    @Test
    void testPathFilterDispatchesApiRequests() {
        var filter = WebServiceFilter.byPath("/api");
        var ctx = new DefaultContext();
        var apiReq = HttpRequest.of(HttpMethod.GET, "/api/users");
        var webReq = HttpRequest.of(HttpMethod.GET, "/web/page");
        var adminReq = HttpRequest.of(HttpMethod.GET, "/api/admin");

        var result = filter.filter(ctx, apiReq, webReq, adminReq);
        assertThat(result).hasSize(2);
        assertThat(result[0].getUri()).isEqualTo("/api/users");
        assertThat(result[1].getUri()).isEqualTo("/api/admin");
    }

    @Test
    void testMethodFilterSeparatesReadsFromWrites() {
        var getFilter = WebServiceFilter.byMethod(HttpMethod.GET);
        var ctx = new DefaultContext();
        var get1 = HttpRequest.of(HttpMethod.GET, "/data");
        var post1 = HttpRequest.of(HttpMethod.POST, "/data");
        var get2 = HttpRequest.of(HttpMethod.GET, "/other");
        var put1 = HttpRequest.of(HttpMethod.PUT, "/data");

        var reads = getFilter.filter(ctx, get1, post1, get2, put1);
        assertThat(reads).hasSize(2);
        assertThat(reads[0].getUri()).isEqualTo("/data");
        assertThat(reads[1].getUri()).isEqualTo("/other");
    }

    @Test
    void testContentTypeFilterRoutesJsonOnly() {
        var jsonFilter = WebServiceFilter.byContentType("application/json");
        var ctx = new DefaultContext();

        var jsonReq = HttpRequest.of(HttpMethod.POST, "/submit");
        jsonReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");

        var xmlReq = HttpRequest.of(HttpMethod.POST, "/submit");
        xmlReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/xml");

        var plainReq = HttpRequest.of(HttpMethod.POST, "/submit");
        plainReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");

        var result = jsonFilter.filter(ctx, jsonReq, xmlReq, plainReq);
        assertThat(result).hasSize(1);
        assertThat(result[0].getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void testChainedFilteringPathThenMethod() {
        var pathFilter = WebServiceFilter.byPath("/api");
        var methodFilter = WebServiceFilter.byMethod(HttpMethod.POST);
        var ctx = new DefaultContext();

        var apiGet = HttpRequest.of(HttpMethod.GET, "/api/data");
        var apiPost = HttpRequest.of(HttpMethod.POST, "/api/data");
        var webPost = HttpRequest.of(HttpMethod.POST, "/web/data");

        var pathFiltered = pathFilter.filter(ctx, apiGet, apiPost, webPost);
        assertThat(pathFiltered).hasSize(2);

        var methodFiltered = methodFilter.filter(ctx, pathFiltered);
        assertThat(methodFiltered).hasSize(1);
        assertThat(methodFiltered[0].getUri()).isEqualTo("/api/data");
        assertThat(methodFiltered[0].getMethod()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void testContentTypeFilterWithCharset() {
        var jsonFilter = WebServiceFilter.byContentType("application/json");
        var ctx = new DefaultContext();

        var req = HttpRequest.of(HttpMethod.POST, "/data");
        req.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8");

        var result = jsonFilter.filter(ctx, req);
        assertThat(result).hasSize(1);
    }

    @Test
    void testFilterEmptyInput() {
        var filter = WebServiceFilter.byPath("/api");
        var ctx = new DefaultContext();
        var result = filter.filter(ctx);
        assertThat(result).isEmpty();
    }
}
