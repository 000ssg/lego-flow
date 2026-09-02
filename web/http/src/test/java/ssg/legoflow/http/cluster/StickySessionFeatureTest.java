package ssg.legoflow.http.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class StickySessionFeatureTest {

    private ClusterNode node(String id, int port) {
        return ClusterNode.builder()
                .id(id)
                .host("127.0.0.1")
                .port(port)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    @Test
    void default_constructor() {
        StickySessionFeature feature = new StickySessionFeature();
        assertThat(feature.getName()).isEqualTo("sticky-sessions");
        assertThat(feature.getCategory()).isEqualTo(HttpFeatureCategory.CLUSTER);
        assertThat(feature.isCore()).isFalse();
    }

    @Test
    void configure_with_params() {
        StickySessionFeature feature = new StickySessionFeature();
        feature.configure(Map.of(
                "cookieName", "my-session",
                "maxAgeSeconds", 1800,
                "secure", true,
                "httpOnly", false,
                "fallback", "redirect"
        ));

        // After configuration, the feature should have updated config
        var nodes = List.of(node("n1", 8080), node("n2", 8081));
        feature.updateNodes(nodes);

        // Test routing with new config
        var headers = new HttpHeaders();
        headers.set("Cookie", "my-session=n1");
        var request = new HttpRequest(HttpMethod.GET, "/test",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        ClusterNode target = feature.route(request);
        assertThat(target).isNotNull();
        assertThat(target.id()).isEqualTo("n1");
    }

    @Test
    void configure_with_empty_params() {
        StickySessionFeature feature = new StickySessionFeature();
        feature.configure(Map.of()); // No-op
        feature.configure(null); // No-op

        assertThat(feature.getName()).isEqualTo("sticky-sessions");
    }

    @Test
    void install_in_registry() {
        var registry = new HttpFeatureRegistry();
        StickySessionFeature feature = new StickySessionFeature();
        feature.install(registry);

        assertThat(registry.isEnabled("sticky-sessions")).isTrue();
        assertThat(registry.getFeature("sticky-sessions")).isSameAs(feature);
    }

    @Test
    void updateNodes_and_route() {
        var config = SessionAffinityConfig.builder().build();
        var hasher = new StickySessionHasher();
        StickySessionFeature feature = new StickySessionFeature(config, hasher);

        var nodes = List.of(node("n1", 8080), node("n2", 8081));
        feature.updateNodes(nodes);

        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        ClusterNode target = feature.route(request);

        assertThat(target).isNotNull();
        assertThat(target.id()).isIn("n1", "n2");
    }

    @Test
    void setSessionCookie_on_response() {
        var config = SessionAffinityConfig.builder()
                .cookieName("cluster-session")
                .build();
        var hasher = new StickySessionHasher();
        StickySessionFeature feature = new StickySessionFeature(config, hasher);

        var response = HttpResponse.of(HttpStatus.OK, "response body");
        var node = node("node-42", 8080);

        feature.setSessionCookie(response, node);

        String cookie = response.getHeaders().get("Set-Cookie");
        assertThat(cookie).contains("cluster-session=node-42");
    }

    @Test
    void router_accessor() {
        var config = SessionAffinityConfig.builder().build();
        var hasher = new StickySessionHasher();
        StickySessionFeature feature = new StickySessionFeature(config, hasher);

        var router = feature.router();
        assertThat(router).isNotNull();
        assertThat(router.cookieName()).isEqualTo("X-Session-Node");
    }

    @Test
    void sticky_routing_with_cookie() {
        var config = SessionAffinityConfig.builder().build();
        var hasher = new StickySessionHasher();
        StickySessionFeature feature = new StickySessionFeature(config, hasher);

        var nodes = List.of(node("n1", 8080), node("n2", 8081));
        feature.updateNodes(nodes);

        // Multiple requests with same cookie → same node
        var headers = new HttpHeaders();
        headers.set("Cookie", "X-Session-Node=n1");
        var request = new HttpRequest(HttpMethod.GET, "/api/users",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        for (int i = 0; i < 5; i++) {
            ClusterNode target = feature.route(request);
            assertThat(target.id()).isEqualTo("n1");
        }
    }

    @Test
    void configure_invalid_fallback_throws() {
        StickySessionFeature feature = new StickySessionFeature();
        assertThatThrownBy(() -> feature.configure(Map.of("fallback", "INVALID")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configure_invalid_maxAge_throws() {
        StickySessionFeature feature = new StickySessionFeature();
        assertThatThrownBy(() -> feature.configure(Map.of("maxAgeSeconds", "not-a-number")))
                .isInstanceOf(NumberFormatException.class);
    }
}
