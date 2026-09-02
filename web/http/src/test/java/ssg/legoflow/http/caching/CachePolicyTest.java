package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CachePolicyTest {

    private CachePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CachePolicy();
    }

    @Test
    void testDefaultMaxAge() {
        assertThat(policy.getDefaultMaxAge()).isEqualTo(3600);
    }

    @Test
    void testSetAndGetDefaultMaxAge() {
        policy.setDefaultMaxAge(7200);
        assertThat(policy.getDefaultMaxAge()).isEqualTo(7200);
    }

    @Test
    void testIsCacheableGetWithOkStatus() {
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.GET, 200, cc)).isTrue();
    }

    @Test
    void testIsCacheableHeadWithOkStatus() {
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.HEAD, 200, cc)).isTrue();
    }

    @Test
    void testIsCacheablePostIsNotCached() {
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.POST, 200, cc)).isFalse();
    }

    @Test
    void testIsCacheableNoStorePreventsCaching() {
        CacheControl cc = CacheControl.parse("no-store");
        assertThat(policy.isCacheable(HttpMethod.GET, 200, cc)).isFalse();
    }

    @Test
    void testIsCacheablePrivateByDefaultNotCached() {
        CacheControl cc = CacheControl.parse("private");
        assertThat(policy.isCacheable(HttpMethod.GET, 200, cc)).isFalse();
    }

    @Test
    void testIsCacheablePrivateWhenAllowed() {
        policy.setCachePrivate(true);
        CacheControl cc = CacheControl.parse("private");
        assertThat(policy.isCacheable(HttpMethod.GET, 200, cc)).isTrue();
    }

    @Test
    void testIsCacheableWithNullCacheControl() {
        // No cache control means use defaults - GET with 200 should be cacheable
        assertThat(policy.isCacheable(HttpMethod.GET, 200, null)).isTrue();
    }

    @Test
    void testIsCacheableNotOkStatus() {
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.GET, 500, cc)).isFalse();
    }

    @Test
    void testGetEffectiveMaxAgeFromCacheControl() {
        CacheControl cc = CacheControl.parse("max-age=7200");
        assertThat(policy.getEffectiveMaxAge(cc)).isEqualTo(7200);
    }

    @Test
    void testGetEffectiveMaxAgeDefaultWhenNoCacheControl() {
        policy.setDefaultMaxAge(1800);
        assertThat(policy.getEffectiveMaxAge(null)).isEqualTo(1800);
    }

    @Test
    void testGetEffectiveMaxAgeDefaultWhenNegativeMaxAge() {
        CacheControl cc = CacheControl.parse("");
        policy.setDefaultMaxAge(900);
        assertThat(policy.getEffectiveMaxAge(cc)).isEqualTo(900);
    }

    @Test
    void testSetCachePrivate() {
        assertThat(policy.getDefaultMaxAge()).isEqualTo(3600);
        policy.setCachePrivate(true);
    }

    @Test
    void testIsCacheableWithNotModifiedStatus() {
        // 304 is not in the default cacheable status codes
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.GET, 304, cc)).isFalse();
    }

    @Test
    void testIsCacheableWithNoContentStatus() {
        // 204 is in the default cacheable status codes
        CacheControl cc = CacheControl.parse("max-age=3600");
        assertThat(policy.isCacheable(HttpMethod.GET, 204, cc)).isTrue();
    }
}
