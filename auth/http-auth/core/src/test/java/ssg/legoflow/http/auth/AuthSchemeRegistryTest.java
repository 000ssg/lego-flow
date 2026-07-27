package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthSchemeRegistryTest {

    private AuthSchemeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AuthSchemeRegistry();
    }

    @Test
    void testRegisterAndGet() {
        var scheme = createDummyScheme("Basic");
        registry.register(scheme);
        assertThat(registry.get("Basic")).isPresent();
        assertThat(registry.get("Basic").get().schemeName()).isEqualTo("Basic");
    }

    @Test
    void testCaseInsensitiveLookup() {
        registry.register(createDummyScheme("Bearer"));
        assertThat(registry.get("bearer")).isPresent();
        assertThat(registry.get("BEARER")).isPresent();
        assertThat(registry.get("Bearer")).isPresent();
    }

    @Test
    void testGetNonExistent() {
        assertThat(registry.get("NonExistent")).isEmpty();
        assertThat(registry.get(null)).isEmpty();
    }

    @Test
    void testRemove() {
        registry.register(createDummyScheme("Basic"));
        assertThat(registry.remove("basic")).isTrue();
        assertThat(registry.get("Basic")).isEmpty();
    }

    @Test
    void testRemoveNonExistent() {
        assertThat(registry.remove("nothing")).isFalse();
        assertThat(registry.remove(null)).isFalse();
    }

    @Test
    void testSize() {
        assertThat(registry.size()).isEqualTo(0);
        registry.register(createDummyScheme("Basic"));
        assertThat(registry.size()).isEqualTo(1);
        registry.register(createDummyScheme("Bearer"));
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void testSchemeNames() {
        registry.register(createDummyScheme("Basic"));
        registry.register(createDummyScheme("Bearer"));
        assertThat(registry.schemeNames()).containsExactlyInAnyOrder("basic", "bearer");
    }

    @Test
    void testSchemes() {
        registry.register(createDummyScheme("Basic"));
        registry.register(createDummyScheme("Bearer"));
        assertThat(registry.schemes()).hasSize(2);
    }

    @Test
    void testRegisterNullThrows() {
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testChaining() {
        registry.register(createDummyScheme("Basic"))
                .register(createDummyScheme("Bearer"));
        assertThat(registry.size()).isEqualTo(2);
    }

    private AuthenticationScheme createDummyScheme(String name) {
        return new AuthenticationScheme() {
            @Override public String schemeName() { return name; }
            @Override public AuthResult authenticate(HttpRequest request, AuthContext context) {
                return AuthResult.failure("dummy");
            }
            @Override public void challenge(HttpResponse response, AuthContext context) {}
            @Override public AuthCredentials extractCredentials(HttpRequest request) {
                return new AuthCredentials.None();
            }
        };
    }
}
