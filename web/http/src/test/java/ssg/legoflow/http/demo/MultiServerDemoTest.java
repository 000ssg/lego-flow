package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.multi.LoadBalancedDemo;
import ssg.legoflow.http.demo.multi.MultiServerDemo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MultiServerDemoTest {

    @Test
    void testMultiServerHasThreeServers() {
        var demo = new MultiServerDemo();

        assertThat(demo.getServers()).hasSize(3);
    }

    @Test
    void testMinimalServerResponds() {
        var demo = new MultiServerDemo();
        var ctx = new DefaultContext();
        var request = HttpRequest.of(HttpMethod.GET, "/");

        var response = demo.getMinimalServer().handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Minimal Server");
    }

    @Test
    void testStandardServerResponds() {
        var demo = new MultiServerDemo();
        var ctx = new DefaultContext();
        var request = HttpRequest.of(HttpMethod.GET, "/");

        var response = demo.getStandardServer().handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Standard Server");
    }

    @Test
    void testFullServerResponds() {
        var demo = new MultiServerDemo();
        var ctx = new DefaultContext();
        var request = HttpRequest.of(HttpMethod.GET, "/");

        var response = demo.getFullServer().handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Full Server");
    }

    @Test
    void testServerInfoEndpoints() {
        var demo = new MultiServerDemo(9001, 9002, 9003);
        var ctx = new DefaultContext();

        var minResp = demo.getMinimalServer().handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/info"));
        assertThat(minResp.getBodyAsString()).contains("\"profile\":\"minimal\"");
        assertThat(minResp.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");

        var stdResp = demo.getStandardServer().handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/info"));
        assertThat(stdResp.getBodyAsString()).contains("\"profile\":\"standard\"");

        var fullResp = demo.getFullServer().handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/info"));
        assertThat(fullResp.getBodyAsString()).contains("\"profile\":\"full\"");
    }

    @Test
    void testDifferentPorts() {
        var demo = new MultiServerDemo(9010, 9011, 9012);

        assertThat(demo.getMinimalServer().getConfig().getPort()).isEqualTo(9010);
        assertThat(demo.getStandardServer().getConfig().getPort()).isEqualTo(9011);
        assertThat(demo.getFullServer().getConfig().getPort()).isEqualTo(9012);
    }

    @Test
    void testLoadBalancerDistribution() {
        var lb = new LoadBalancedDemo(3, 9100);

        var resp0 = lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        assertThat(resp0.getBodyAsString()).isEqualTo("Backend-0");

        var resp1 = lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        assertThat(resp1.getBodyAsString()).isEqualTo("Backend-1");

        var resp2 = lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        assertThat(resp2.getBodyAsString()).isEqualTo("Backend-2");

        var resp3 = lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        assertThat(resp3.getBodyAsString()).isEqualTo("Backend-0");
    }

    @Test
    void testLoadBalancerRequestCount() {
        var lb = new LoadBalancedDemo(2, 9200);

        lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));
        lb.dispatch(HttpRequest.of(HttpMethod.GET, "/"));

        assertThat(lb.getRequestCount()).isEqualTo(3);
    }

    @Test
    void testLoadBalancerBackendIndex() {
        var lb = new LoadBalancedDemo(3, 9300);

        assertThat(lb.getBackendIndex(0)).isEqualTo(0);
        assertThat(lb.getBackendIndex(1)).isEqualTo(1);
        assertThat(lb.getBackendIndex(2)).isEqualTo(2);
        assertThat(lb.getBackendIndex(3)).isEqualTo(0);
    }
}
