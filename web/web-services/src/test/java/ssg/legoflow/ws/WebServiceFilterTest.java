package ssg.legoflow.ws;

import org.junit.jupiter.api.Test;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebServiceFilter}.
 *
 * @since 0.1.0
 */
class WebServiceFilterTest {

    private final HttpRequest reqApiGet = HttpRequest.of(HttpMethod.GET, "/api/users");
    private final HttpRequest reqApiPost = HttpRequest.of(HttpMethod.POST, "/api/users");
    private final HttpRequest reqStaticGet = HttpRequest.of(HttpMethod.GET, "/static/index.html");

    @Test
    void testByPathFilter() {
        var filter = WebServiceFilter.byPath("/api/");
        var result = filter.doFilter(null, new HttpRequest[]{reqApiGet, reqStaticGet});
        assertThat(result).hasSize(1);
        assertThat(result[0].getUri()).isEqualTo("/api/users");
    }

    @Test
    void testByMethodFilter() {
        var filter = WebServiceFilter.byMethod(HttpMethod.GET);
        var result = filter.doFilter(null, new HttpRequest[]{reqApiGet, reqApiPost});
        assertThat(result).hasSize(1);
        assertThat(result[0].getMethod()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void testByContentTypeFilter() {
        var jsonReq = HttpRequest.of(HttpMethod.POST, "/api/data");
        jsonReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
        
        var htmlReq = HttpRequest.of(HttpMethod.POST, "/form");
        htmlReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/html");

        var filter = WebServiceFilter.byContentType("json");
        var result = filter.doFilter(null, new HttpRequest[]{jsonReq, htmlReq});
        assertThat(result).hasSize(1);
    }

    @Test
    void testByContentTypeMissingHeaderExcludes() {
        var noCtReq = HttpRequest.of(HttpMethod.POST, "/api/data");
        var hasCtReq = HttpRequest.of(HttpMethod.POST, "/api/data2");
        hasCtReq.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");

        var filter = WebServiceFilter.byContentType("json");
        var result = filter.doFilter(null, new HttpRequest[]{noCtReq, hasCtReq});
        assertThat(result).hasSize(1);
    }

    @Test
    void testPredicateFilterAllPass() {
        var filter = new WebServiceFilter(req -> true);
        var result = filter.doFilter(null, new HttpRequest[]{reqApiGet, reqStaticGet});
        assertThat(result).hasSize(2);
    }

    @Test
    void testPredicateFilterNonePass() {
        var filter = new WebServiceFilter(req -> false);
        var result = filter.doFilter(null, new HttpRequest[]{reqApiGet, reqStaticGet});
        assertThat(result).isEmpty();
    }

    @Test
    void testEmptyDataReturnsEmptyArray() {
        var filter = WebServiceFilter.byPath("/api/");
        var result = filter.doFilter(null, new HttpRequest[]{});
        assertThat(result).isEmpty();
    }

    @Test
    void testStateAndStatistics() {
        var filter = WebServiceFilter.byPath("/api/");
        // Initially in IDLE state with empty stats
        assertThat(filter.getState().name()).isEqualTo("IDLE");
        assertThat(filter.getStatistics()).isNotNull();
    }

    @Test
    void testCloseSetsStoppedState() {
        var filter = WebServiceFilter.byPath("/api/");
        filter.close();
        assertThat(filter.getState().name()).isEqualTo("STOPPED");
    }
}
