package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link StructuredData}.
 */
class StructuredDataTest {

    @Test
    void testCreateWithId() {
        var sd = StructuredData.of("exampleSDID");
        assertThat(sd.id()).isEqualTo("exampleSDID");
        assertThat(sd.params()).isEmpty();
    }

    @Test
    void testCreateWithParams() {
        var sd = StructuredData.of("myID", Map.of("key1", "val1", "key2", "val2"));
        assertThat(sd.id()).isEqualTo("myID");
        assertThat(sd.params()).hasSize(2);
        assertThat(sd.params().get("key1")).isEqualTo("val1");
    }

    @Test
    void testBuilder() {
        var sd = StructuredData.builder("timeQuality")
                .param("tzKnown", "1")
                .param("isSynced", "1")
                .build();
        assertThat(sd.id()).isEqualTo("timeQuality");
        assertThat(sd.params()).hasSize(2);
        assertThat(sd.params().get("tzKnown")).isEqualTo("1");
        assertThat(sd.params().get("isSynced")).isEqualTo("1");
    }

    @Test
    void testEncodeNoParams() {
        var sd = StructuredData.of("myID");
        assertThat(sd.encode()).isEqualTo("[myID]");
    }

    @Test
    void testEncodeWithParams() {
        var sd = StructuredData.builder("exampleSDID@32473")
                .param("iut", "3")
                .param("eventSource", "Application")
                .build();
        String encoded = sd.encode();
        assertThat(encoded).startsWith("[exampleSDID@32473");
        assertThat(encoded).contains("iut=\"3\"");
        assertThat(encoded).contains("eventSource=\"Application\"");
        assertThat(encoded).endsWith("]");
    }

    @Test
    void testEncodeEscaping() {
        var sd = StructuredData.builder("test")
                .param("msg", "contains \" and ] and \\")
                .build();
        String encoded = sd.encode();
        assertThat(encoded).contains("contains \\\" and \\] and \\\\");
    }

    @Test
    void testNullIdThrows() {
        assertThatThrownBy(() -> StructuredData.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptyIdThrows() {
        assertThatThrownBy(() -> StructuredData.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidIdCharacters() {
        assertThatThrownBy(() -> StructuredData.of("has=equals"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredData.of("has]bracket"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredData.of("has\"quote"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredData.of("has space"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWellKnownIds() {
        assertThat(StructuredData.TIME_QUALITY).isEqualTo("timeQuality");
        assertThat(StructuredData.ORIGIN).isEqualTo("origin");
        assertThat(StructuredData.META).isEqualTo("meta");
    }

    @Test
    void testImmutableParams() {
        var sd = StructuredData.of("test", Map.of("k", "v"));
        assertThatThrownBy(() -> sd.params().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
