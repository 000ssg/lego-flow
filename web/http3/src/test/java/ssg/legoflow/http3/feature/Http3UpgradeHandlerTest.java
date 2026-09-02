package ssg.legoflow.http3.feature;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class Http3UpgradeHandlerTest {

    private final Http3UpgradeHandler handler = new Http3UpgradeHandler();

    @Test
    void testGenerateAltSvcHeaderSameHost() {
        // Given/When
        var header = handler.generateAltSvcHeader("", 443);

        // Then
        assertThat(header).isEqualTo("h3=\":443\"; ma=86400");
    }

    @Test
    void testGenerateAltSvcHeaderWithHost() {
        // Given/When
        var header = handler.generateAltSvcHeader("example.com", 8443);

        // Then
        assertThat(header).isEqualTo("h3=\"example.com:8443\"; ma=86400");
    }

    @Test
    void testParseAltSvcSimple() {
        // Given
        var header = "h3=\":443\"";

        // When
        var result = handler.parseAltSvc(header);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().protocol()).isEqualTo("h3");
        assertThat(result.get().host()).isEmpty();
        assertThat(result.get().port()).isEqualTo(443);
    }

    @Test
    void testParseAltSvcWithMaxAge() {
        // Given
        var header = "h3=\":443\"; ma=3600";

        // When
        var result = handler.parseAltSvc(header);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().maxAge()).isEqualTo(3600);
    }

    @Test
    void testParseAltSvcWithHost() {
        // Given
        var header = "h3=\"alt.example.com:8443\"; ma=86400";

        // When
        var result = handler.parseAltSvc(header);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().protocol()).isEqualTo("h3");
        assertThat(result.get().host()).isEqualTo("alt.example.com");
        assertThat(result.get().port()).isEqualTo(8443);
        assertThat(result.get().maxAge()).isEqualTo(86400);
    }

    @Test
    void testParseAltSvcClear() {
        // Given
        var header = "clear";

        // When
        var result = handler.parseAltSvc(header);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testParseAltSvcNull() {
        // Given/When
        var result = handler.parseAltSvc(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testParseAltSvcEmpty() {
        // Given/When
        var result = handler.parseAltSvc("");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testIsHttp3Available() {
        // Given/When/Then
        assertThat(handler.isHttp3Available("h3=\":443\"")).isTrue();
        assertThat(handler.isHttp3Available("h2=\":443\"")).isFalse();
        assertThat(handler.isHttp3Available("clear")).isFalse();
        assertThat(handler.isHttp3Available(null)).isFalse();
    }

    @Test
    void testRoundtrip() {
        // Given
        var generated = handler.generateAltSvcHeader("example.com", 443);

        // When
        var parsed = handler.parseAltSvc(generated);

        // Then
        assertThat(parsed).isPresent();
        assertThat(parsed.get().protocol()).isEqualTo("h3");
        assertThat(parsed.get().host()).isEqualTo("example.com");
        assertThat(parsed.get().port()).isEqualTo(443);
    }
}
