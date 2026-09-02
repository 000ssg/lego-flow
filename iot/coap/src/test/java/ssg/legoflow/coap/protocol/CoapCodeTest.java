package ssg.legoflow.coap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapCode}.
 *
 * @since 0.1.0
 */
class CoapCodeTest {

    @Test
    void testEncodeMethodCode() {
        // Given: GET code (0.01)
        var code = CoapCode.GET;

        // When: encoded
        int encoded = code.encode();

        // Then: (0 << 5) | 1 = 1
        assertThat(encoded).isEqualTo(1);
    }

    @Test
    void testDecodeMethodCode() {
        // Given: encoded GET (1)
        int encoded = 1;

        // When: decoded
        var code = CoapCode.decode(encoded);

        // Then: class 0, detail 1
        assertThat(code.codeClass()).isZero();
        assertThat(code.codeDetail()).isEqualTo(1);
        assertThat(code).isEqualTo(CoapCode.GET);
    }

    @Test
    void testEncodeResponseCode() {
        // Given: Content code (2.05)
        var code = CoapCode.CONTENT;

        // When: encoded
        int encoded = code.encode();

        // Then: (2 << 5) | 5 = 69
        assertThat(encoded).isEqualTo(69);
    }

    @Test
    void testDecodeResponseCode() {
        // Given: encoded Content (69)
        var code = CoapCode.decode(69);

        // Then
        assertThat(code.codeClass()).isEqualTo(2);
        assertThat(code.codeDetail()).isEqualTo(5);
        assertThat(code).isEqualTo(CoapCode.CONTENT);
    }

    @Test
    void testIsMethod() {
        assertThat(CoapCode.GET.isMethod()).isTrue();
        assertThat(CoapCode.POST.isMethod()).isTrue();
        assertThat(CoapCode.PUT.isMethod()).isTrue();
        assertThat(CoapCode.DELETE.isMethod()).isTrue();
        assertThat(CoapCode.FETCH.isMethod()).isTrue();
        assertThat(CoapCode.PATCH.isMethod()).isTrue();
        assertThat(CoapCode.iPATCH.isMethod()).isTrue();
        assertThat(CoapCode.CONTENT.isMethod()).isFalse();
        assertThat(CoapCode.NOT_FOUND.isMethod()).isFalse();
    }

    @Test
    void testIsSuccess() {
        assertThat(CoapCode.CREATED.isSuccess()).isTrue();
        assertThat(CoapCode.CONTENT.isSuccess()).isTrue();
        assertThat(CoapCode.CHANGED.isSuccess()).isTrue();
        assertThat(CoapCode.NOT_FOUND.isSuccess()).isFalse();
        assertThat(CoapCode.GET.isSuccess()).isFalse();
    }

    @Test
    void testIsClientError() {
        assertThat(CoapCode.BAD_REQUEST.isClientError()).isTrue();
        assertThat(CoapCode.NOT_FOUND.isClientError()).isTrue();
        assertThat(CoapCode.UNAUTHORIZED.isClientError()).isTrue();
        assertThat(CoapCode.CONTENT.isClientError()).isFalse();
    }

    @Test
    void testIsServerError() {
        assertThat(CoapCode.INTERNAL_SERVER_ERROR.isServerError()).isTrue();
        assertThat(CoapCode.BAD_GATEWAY.isServerError()).isTrue();
        assertThat(CoapCode.NOT_FOUND.isServerError()).isFalse();
        assertThat(CoapCode.CONTENT.isServerError()).isFalse();
    }
}
