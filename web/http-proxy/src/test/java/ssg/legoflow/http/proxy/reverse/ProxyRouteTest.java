package ssg.legoflow.http.proxy.reverse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRouteTest {

    @Test
    void testMatchesExactPath() {
        var route = ProxyRoute.of("/api", new BackendServer("b", 8080));
        assertThat(route.matches("/api")).isTrue();
    }

    @Test
    void testMatchesSubPath() {
        var route = ProxyRoute.of("/api", new BackendServer("b", 8080));
        assertThat(route.matches("/api/users")).isTrue();
    }

    @Test
    void testDoesNotMatchDifferentPath() {
        var route = ProxyRoute.of("/api", new BackendServer("b", 8080));
        assertThat(route.matches("/web/users")).isFalse();
    }

    @Test
    void testRootPathMatchesAll() {
        var route = ProxyRoute.of("/", new BackendServer("b", 8080));
        assertThat(route.matches("/anything")).isTrue();
        assertThat(route.matches("/api/users")).isTrue();
    }

    @Test
    void testDoesNotMatchPartialPrefix() {
        var route = ProxyRoute.of("/api", new BackendServer("b", 8080));
        assertThat(route.matches("/api2/users")).isFalse();
    }

    @Test
    void testRewritePathWithStripping() {
        var route = new ProxyRoute("/api", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), true);
        assertThat(route.rewritePath("/api/users")).isEqualTo("/users");
    }

    @Test
    void testRewritePathWithoutStripping() {
        var route = new ProxyRoute("/api", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), false);
        assertThat(route.rewritePath("/api/users")).isEqualTo("/api/users");
    }

    @Test
    void testRewritePathExactMatch() {
        var route = new ProxyRoute("/api", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), true);
        assertThat(route.rewritePath("/api")).isEqualTo("/");
    }

    @Test
    void testRewritePathRootPrefixNoStrip() {
        var route = new ProxyRoute("/", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), true);
        assertThat(route.rewritePath("/any/path")).isEqualTo("/any/path");
    }

    @Test
    void testSelectBackend() {
        var b = new BackendServer("b", 8080);
        var route = ProxyRoute.of("/api", b);
        assertThat(route.selectBackend()).isEqualTo(b);
    }

    @Test
    void testSelectBackendNoHealthy() {
        var b = new BackendServer("b", 8080);
        b.setHealthy(false);
        var route = ProxyRoute.of("/api", b);
        assertThat(route.selectBackend()).isNull();
    }

    @Test
    void testGetPathPrefix() {
        var route = ProxyRoute.of("/api/v1", new BackendServer("b", 8080));
        assertThat(route.getPathPrefix()).isEqualTo("/api/v1");
    }

    @Test
    void testGetBackends() {
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        var route = ProxyRoute.of("/api", List.of(b1, b2));
        assertThat(route.getBackends()).hasSize(2);
    }

    @Test
    void testTrailingSlashNormalized() {
        var route = ProxyRoute.of("/api/", new BackendServer("b", 8080));
        assertThat(route.getPathPrefix()).isEqualTo("/api");
        assertThat(route.matches("/api/users")).isTrue();
    }

    @Test
    void testIsStripPrefix() {
        var stripping = new ProxyRoute("/api", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), true);
        var keeping = new ProxyRoute("/api", List.of(new BackendServer("b", 8080)),
                new RoundRobinBalancer(), false);
        assertThat(stripping.isStripPrefix()).isTrue();
        assertThat(keeping.isStripPrefix()).isFalse();
    }

    @Test
    void testToString() {
        var route = ProxyRoute.of("/api", new BackendServer("b", 8080));
        assertThat(route.toString()).contains("/api");
    }

    @Test
    void testFactoryMethodSingleBackend() {
        var b = new BackendServer("b", 8080);
        var route = ProxyRoute.of("/path", b);
        assertThat(route.getBackends()).containsExactly(b);
        assertThat(route.isStripPrefix()).isFalse();
    }

    @Test
    void testFactoryMethodMultipleBackends() {
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        var route = ProxyRoute.of("/path", List.of(b1, b2));
        assertThat(route.getBackends()).containsExactly(b1, b2);
    }
}
