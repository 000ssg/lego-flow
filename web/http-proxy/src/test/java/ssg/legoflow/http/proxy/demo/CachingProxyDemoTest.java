package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachingProxyDemoTest {

    private CachingProxyDemo demo;

    @BeforeEach
    void setUp() {
        demo = new CachingProxyDemo();
    }

    @Test
    void testDemoRuns() {
        String result = demo.run();
        assertThat(result).isNotEmpty();
        assertThat(result).contains("miss");
        assertThat(result).contains("hit");
    }

    @Test
    void testFirstRequestIsMiss() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/fresh"));
        assertThat(demo.getCachingProxy().getCacheMisses()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testSecondRequestIsHit() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        assertThat(demo.getCachingProxy().getCacheHits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testCacheInvalidation() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.POST, "/data"));
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        // After POST, the second GET should be a miss
        assertThat(demo.getCachingProxy().getCacheMisses()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testConditionalRequest() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        var conditionalReq = HttpRequest.of(HttpMethod.GET, "/data");
        conditionalReq.getHeaders().set(HttpHeaders.IF_NONE_MATCH,
                "\"etag-" + "/data".hashCode() + "\"");
        var response = demo.getCachingProxy().handleRequest(conditionalReq);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void testCacheStoreSize() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/a"));
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/b"));
        assertThat(demo.getCachingProxy().getCacheStore().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testDemoStatsAfterRun() {
        demo.run();
        assertThat(demo.getCachingProxy().getCacheHits()).isGreaterThan(0);
        assertThat(demo.getCachingProxy().getCacheMisses()).isGreaterThan(0);
    }

    @Test
    void testCacheHitHeader() {
        demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/cached"));
        var response = demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/cached"));
        assertThat(response.getHeaders().get("x-cache")).isEqualTo("HIT");
    }

    @Test
    void testGetRequestCacheable() {
        var response = demo.getCachingProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/page"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(demo.getCachingProxy().getCacheStore().size()).isGreaterThanOrEqualTo(1);
    }
}
