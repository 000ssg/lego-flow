package ssg.legoflow.http.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtHeaderTest {

    @Test
    void testOf() {
        var h = JwtHeader.of("HS256");
        assertThat(h.alg()).isEqualTo("HS256");
        assertThat(h.typ()).isEqualTo("JWT");
    }

    @Test
    void testToJson() {
        var h = JwtHeader.of("RS256");
        assertThat(h.toJson()).isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
    }

    @Test
    void testFromJson() {
        var h = JwtHeader.fromJson("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        assertThat(h.alg()).isEqualTo("HS256");
        assertThat(h.typ()).isEqualTo("JWT");
    }

    @Test
    void testNullAlgThrows() {
        assertThatThrownBy(() -> new JwtHeader(null, "JWT"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullTypDefaultsToJwt() {
        var h = new JwtHeader("HS256", null);
        assertThat(h.typ()).isEqualTo("JWT");
    }

    @Test
    void testFromJsonMissingAlg() {
        var h = JwtHeader.fromJson("{\"typ\":\"JWT\"}");
        assertThat(h.alg()).isEqualTo("none");
    }
}
