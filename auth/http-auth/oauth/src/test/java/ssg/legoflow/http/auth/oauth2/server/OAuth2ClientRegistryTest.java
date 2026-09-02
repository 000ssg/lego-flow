package ssg.legoflow.http.auth.oauth2.server;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class OAuth2ClientRegistryTest {

    @Test
    void testRegisterAndGet() {
        var registry = new OAuth2ClientRegistry();
        var client = new OAuth2ClientRegistry.RegisteredClient(
                "app1", "secret", Set.of("http://localhost/cb"), Set.of("read"), Set.of("authorization_code"), true);
        registry.register(client);
        assertThat(registry.get("app1")).isPresent();
        assertThat(registry.get("unknown")).isEmpty();
    }

    @Test
    void testAuthenticate() {
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "app1", "secret", Set.of(), Set.of(), Set.of(), true));
        assertThat(registry.authenticate("app1", "secret")).isPresent();
        assertThat(registry.authenticate("app1", "wrong")).isEmpty();
        assertThat(registry.authenticate("unknown", "secret")).isEmpty();
    }

    @Test
    void testPublicClient() {
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "pub", null, Set.of(), Set.of(), Set.of(), false));
        assertThat(registry.authenticate("pub", null)).isPresent();
    }

    @Test
    void testIsRedirectUriAllowed() {
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "app1", "s", Set.of("http://localhost/cb", "http://localhost/auth"), Set.of(), Set.of(), true));
        assertThat(registry.isRedirectUriAllowed("app1", "http://localhost/cb")).isTrue();
        assertThat(registry.isRedirectUriAllowed("app1", "http://evil.com")).isFalse();
        assertThat(registry.isRedirectUriAllowed("unknown", "http://localhost/cb")).isFalse();
    }

    @Test
    void testRemove() {
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "app1", "s", Set.of(), Set.of(), Set.of(), true));
        registry.remove("app1");
        assertThat(registry.get("app1")).isEmpty();
    }

    @Test
    void testSize() {
        var registry = new OAuth2ClientRegistry();
        assertThat(registry.size()).isEqualTo(0);
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "a", "s", Set.of(), Set.of(), Set.of(), true));
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void testAll() {
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "a", "s", Set.of(), Set.of(), Set.of(), true));
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "b", "s", Set.of(), Set.of(), Set.of(), true));
        assertThat(registry.all()).hasSize(2);
    }
}
