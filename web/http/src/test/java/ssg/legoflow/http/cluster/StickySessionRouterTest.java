package ssg.legoflow.http.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class StickySessionRouterTest {

    private ClusterNode node(String id, int port) {
        return ClusterNode.builder()
                .id(id)
                .host("127.0.0.1")
                .port(port)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    private SessionAffinityConfig config() {
        return SessionAffinityConfig.builder().build();
    }

    @Test
    void route_without_cookie() {
        var config = config();
        var hasher = new StickySessionHasher();
        var nodes = List.of(node("n1", 8080), node("n2", 8081));
        hasher.updateNodes(nodes);

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        router.updateNodes(nodes);

        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        ClusterNode target = router.route(request);

        assertThat(target).isNotNull();
        assertThat(target.id()).isIn("n1", "n2");
    }

    @Test
    void route_with_sticky_cookie() {
        var config = config();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        var nodes = List.of(node("n1", 8080), node("n2", 8081));
        router.updateNodes(nodes);

        // Create request with session cookie
        var headers = new HttpHeaders();
        headers.set("Cookie", "X-Session-Node=n2; other=value");
        var request = new HttpRequest(HttpMethod.GET, "/api/data",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        ClusterNode target = router.route(request);
        assertThat(target).isNotNull();
        assertThat(target.id()).isEqualTo("n2");
    }

    @Test
    void route_with_down_node_rehash_fallback() {
        var config = SessionAffinityConfig.builder()
                .fallback(SessionAffinityConfig.FallbackStrategy.REHASH)
                .build();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        // Only node-1 and node-3 are active (node-2 is down)
        var activeNodes = List.of(node("n1", 8080), node("n3", 8082));
        router.updateNodes(activeNodes);

        // Request has cookie pointing to down node-2
        var headers = new HttpHeaders();
        headers.set("Cookie", "X-Session-Node=n2");
        var request = new HttpRequest(HttpMethod.GET, "/api/data",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        ClusterNode target = router.route(request);
        assertThat(target).isNotNull();
        assertThat(target.id()).isIn("n1", "n3"); // Rehashed to available node
    }

    @Test
    void route_with_down_node_error_fallback() {
        var config = SessionAffinityConfig.builder()
                .fallback(SessionAffinityConfig.FallbackStrategy.ERROR)
                .build();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        var activeNodes = List.of(node("n1", 8080));
        router.updateNodes(activeNodes);

        // Request has cookie pointing to non-existent node
        var headers = new HttpHeaders();
        headers.set("Cookie", "X-Session-Node=ghost-node");
        var request = new HttpRequest(HttpMethod.GET, "/api/data",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        ClusterNode target = router.route(request);
        assertThat(target).isNull(); // ERROR strategy returns null
    }

    @Test
    void route_no_active_nodes() {
        var config = config();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        router.updateNodes(List.of());

        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        ClusterNode target = router.route(request);
        assertThat(target).isNull();
    }

    @Test
    void build_cookie() {
        var config = config();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        var node = node("node-42", 8080);

        String cookie = router.buildCookie(node);
        assertThat(cookie).contains("X-Session-Node=node-42");
        assertThat(cookie).contains("Max-Age=");
        assertThat(cookie).contains("Path=/");
    }

    @Test
    void setCookieHeader() {
        var config = SessionAffinityConfig.builder()
                .cookieName("app-session")
                .build();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        var response = HttpResponse.of(HttpStatus.OK, "body");
        var node = node("node-x", 9090);

        router.setCookieHeader(response, node);
        String setCookie = response.getHeaders().get("Set-Cookie");
        assertThat(setCookie).contains("app-session=node-x");
    }

    @Test
    void cookie_name_and_fallback_accessors() {
        var config = SessionAffinityConfig.builder()
                .cookieName("my-cookie")
                .fallback(SessionAffinityConfig.FallbackStrategy.REDIRECT)
                .build();
        var hasher = new StickySessionHasher();

        StickySessionRouter router = new StickySessionRouter(config, hasher);
        assertThat(router.cookieName()).isEqualTo("my-cookie");
        assertThat(router.fallbackStrategy()).isEqualTo(SessionAffinityConfig.FallbackStrategy.REDIRECT);
    }

    @Test
    void activeNodeCount() {
        var config = config();
        var hasher = new StickySessionHasher();
        StickySessionRouter router = new StickySessionRouter(config, hasher);

        assertThat(router.activeNodeCount()).isZero();

        router.updateNodes(List.of(node("n1", 8080)));
        assertThat(router.activeNodeCount()).isEqualTo(1);

        router.updateNodes(List.of(node("n1", 8080), node("n2", 8081)));
        assertThat(router.activeNodeCount()).isEqualTo(2);
    }

    @Test
    void null_config_throws() {
        assertThatThrownBy(() -> new StickySessionRouter(null, new StickySessionHasher()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_hasher_throws() {
        assertThatThrownBy(() -> new StickySessionRouter(config(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_request_throws() {
        var config = config();
        var hasher = new StickySessionHasher();
        StickySessionRouter router = new StickySessionRouter(config, hasher);
        assertThatThrownBy(() -> router.route(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extract_cookie_from_multi_cookie_header() {
        var config = SessionAffinityConfig.builder()
                .cookieName("session-id")
                .build();
        var hasher = new StickySessionHasher();
        StickySessionRouter router = new StickySessionRouter(config, hasher);
        router.updateNodes(List.of(node("n1", 8080), node("n2", 8081)));

        var headers = new HttpHeaders();
        headers.set("Cookie", "other=value1; session-id=n2; third=value3");
        var request = new HttpRequest(HttpMethod.GET, "/test",
                ssg.legoflow.http.core.HttpVersion.HTTP_1_1, headers);

        ClusterNode target = router.route(request);
        assertThat(target).isNotNull();
        assertThat(target.id()).isEqualTo("n2");
    }
}
