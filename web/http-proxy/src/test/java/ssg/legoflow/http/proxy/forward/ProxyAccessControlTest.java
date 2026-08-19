package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
class ProxyAccessControlTest {

    @Test
    void testAllowAll() {
        var ac = ProxyAccessControl.allowAll();
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isTrue();
        assertThat(ac.getDenialReason()).isNull();
    }

    @Test
    void testDenyAll() {
        var ac = ProxyAccessControl.denyAll();
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isFalse();
        assertThat(ac.getDenialReason()).isEqualTo("All requests denied");
    }

    @Test
    void testAllowHostsAllowed() {
        var ac = ProxyAccessControl.allowHosts(Set.of("example.com", "api.example.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isTrue();
        assertThat(ac.getDenialReason()).isNull();
    }

    @Test
    void testAllowHostsDenied() {
        var ac = ProxyAccessControl.allowHosts(Set.of("example.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "other.com", 80)).isFalse();
        assertThat(ac.getDenialReason()).contains("not in allowlist");
    }

    @Test
    void testDenyHostsAllowed() {
        var ac = ProxyAccessControl.denyHosts(Set.of("blocked.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isTrue();
        assertThat(ac.getDenialReason()).isNull();
    }

    @Test
    void testDenyHostsDenied() {
        var ac = ProxyAccessControl.denyHosts(Set.of("blocked.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "blocked.com", 80)).isFalse();
        assertThat(ac.getDenialReason()).contains("denylist");
    }

    @Test
    void testCaseInsensitiveHostMatch() {
        var ac = ProxyAccessControl.allowHosts(Set.of("example.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "EXAMPLE.COM", 80)).isTrue();
    }

    @Test
    void testMultipleAllowedHosts() {
        var ac = ProxyAccessControl.allowHosts(Set.of("a.com", "b.com", "c.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "a.com", 80)).isTrue();
        assertThat(ac.isAllowed(request, "b.com", 80)).isTrue();
        assertThat(ac.isAllowed(request, "c.com", 80)).isTrue();
        assertThat(ac.isAllowed(request, "d.com", 80)).isFalse();
    }

    @Test
    void testDenyHostCaseInsensitive() {
        var ac = ProxyAccessControl.denyHosts(Set.of("blocked.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "BLOCKED.COM", 80)).isFalse();
    }

    @Test
    void testAllowAllDenialReasonNull() {
        var ac = ProxyAccessControl.allowAll();
        ac.isAllowed(HttpRequest.of(HttpMethod.GET, "/"), "any.com", 80);
        assertThat(ac.getDenialReason()).isNull();
    }

    @Test
    void testDenyAllDenialReasonPresent() {
        var ac = ProxyAccessControl.denyAll();
        ac.isAllowed(HttpRequest.of(HttpMethod.GET, "/"), "any.com", 80);
        assertThat(ac.getDenialReason()).isNotNull();
    }

    @Test
    void testEmptyAllowlist() {
        var ac = ProxyAccessControl.allowHosts(Set.of());
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isFalse();
    }

    @Test
    void testEmptyDenylist() {
        var ac = ProxyAccessControl.denyHosts(Set.of());
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isTrue();
    }

    @Test
    void testDifferentPortsSameHost() {
        var ac = ProxyAccessControl.allowHosts(Set.of("example.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(ac.isAllowed(request, "example.com", 80)).isTrue();
        assertThat(ac.isAllowed(request, "example.com", 443)).isTrue();
        assertThat(ac.isAllowed(request, "example.com", 8080)).isTrue();
    }
}
