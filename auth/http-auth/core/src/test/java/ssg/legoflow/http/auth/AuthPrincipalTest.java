package ssg.legoflow.http.auth;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class AuthPrincipalTest {

    @Test
    void testSimplePrincipal() {
        var p = AuthPrincipal.of("alice");
        assertThat(p.getName()).isEqualTo("alice");
        assertThat(p.getRoles()).isEmpty();
        assertThat(p.getAttributes()).isEmpty();
    }

    @Test
    void testPrincipalWithRoles() {
        var p = AuthPrincipal.of("alice", Set.of("admin", "user"));
        assertThat(p.getRoles()).containsExactlyInAnyOrder("admin", "user");
        assertThat(p.hasRole("admin")).isTrue();
        assertThat(p.hasRole("superadmin")).isFalse();
    }

    @Test
    void testPrincipalWithAttributes() {
        var p = new AuthPrincipal("alice", Set.of("user"), Map.of("email", "alice@example.com"));
        assertThat(p.<String>getAttribute("email")).isEqualTo("alice@example.com");
        assertThat(p.<String>getAttribute("nonexistent")).isNull();
    }

    @Test
    void testPrincipalEquality() {
        var a = AuthPrincipal.of("alice");
        var b = AuthPrincipal.of("alice");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testPrincipalInequality() {
        var a = AuthPrincipal.of("alice");
        var b = AuthPrincipal.of("bob");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void testToString() {
        var p = AuthPrincipal.of("alice", Set.of("admin"));
        assertThat(p.toString()).contains("alice").contains("admin");
    }

    @Test
    void testNullNameThrows() {
        assertThatThrownBy(() -> AuthPrincipal.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testRolesAreImmutable() {
        var p = AuthPrincipal.of("alice", Set.of("admin"));
        assertThatThrownBy(() -> p.getRoles().add("user"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testAttributesAreImmutable() {
        var p = new AuthPrincipal("alice", Set.of(), Map.of("key", "val"));
        assertThatThrownBy(() -> p.getAttributes().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
