package ssg.legoflow.http.caching;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CacheControlTest {

    @Test
    void testParseNoCache() {
        // When
        var cc = CacheControl.parse("no-cache");

        // Then
        assertThat(cc.isNoCache()).isTrue();
        assertThat(cc.isNoStore()).isFalse();
    }

    @Test
    void testParseMultipleDirectives() {
        // When
        var cc = CacheControl.parse("no-cache, no-store, must-revalidate");

        // Then
        assertThat(cc.isNoCache()).isTrue();
        assertThat(cc.isNoStore()).isTrue();
        assertThat(cc.isMustRevalidate()).isTrue();
    }

    @Test
    void testParseMaxAge() {
        // When
        var cc = CacheControl.parse("public, max-age=3600");

        // Then
        assertThat(cc.isPublic()).isTrue();
        assertThat(cc.getMaxAge()).isEqualTo(3600);
    }

    @Test
    void testParseSMaxAge() {
        // When
        var cc = CacheControl.parse("s-maxage=600");

        // Then
        assertThat(cc.getSMaxAge()).isEqualTo(600);
    }

    @Test
    void testParseNullReturnsDefaults() {
        // When
        var cc = CacheControl.parse(null);

        // Then
        assertThat(cc.isNoCache()).isFalse();
        assertThat(cc.isNoStore()).isFalse();
        assertThat(cc.getMaxAge()).isEqualTo(-1);
    }

    @Test
    void testToString() {
        // Given
        var cc = new CacheControl()
                .noCache(true)
                .maxAge(300)
                .setPublic(true);

        // When
        String result = cc.toString();

        // Then
        assertThat(result).contains("no-cache");
        assertThat(result).contains("public");
        assertThat(result).contains("max-age=300");
    }

    @Test
    void testParseAndToStringRoundtrip() {
        // Given
        var cc = CacheControl.parse("no-store, private, max-age=0");

        // When
        String result = cc.toString();

        // Then
        assertThat(result).contains("no-store");
        assertThat(result).contains("private");
        assertThat(result).contains("max-age=0");
    }

    @Test
    void testParsePrivateAndNoTransform() {
        // When
        var cc = CacheControl.parse("private, no-transform, proxy-revalidate");

        // Then
        assertThat(cc.isPrivate()).isTrue();
        assertThat(cc.isNoTransform()).isTrue();
        assertThat(cc.isProxyRevalidate()).isTrue();
    }
}
