package ssg.legoflow.http3.config;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class Http3ProfilesTest {

    @Test
    void testServerHttp3Profile() {
        // Given/When
        var profile = Http3Profiles.serverHttp3();

        // Then
        assertThat(profile.getName()).isEqualTo("SERVER_HTTP3");
        assertThat(profile.isEnabled("http3")).isTrue();
        assertThat(profile.isEnabled("core")).isTrue();
        assertThat(profile.getEnabledCategories()).contains(
                HttpFeatureCategory.CORE,
                HttpFeatureCategory.TRANSFER,
                HttpFeatureCategory.CONNECTION,
                HttpFeatureCategory.HTTP3
        );
    }

    @Test
    void testClientHttp3Profile() {
        // Given/When
        var profile = Http3Profiles.clientHttp3();

        // Then
        assertThat(profile.getName()).isEqualTo("CLIENT_HTTP3");
        assertThat(profile.isEnabled("http3")).isTrue();
        assertThat(profile.isEnabled("core")).isTrue();
    }

    @Test
    void testServerHttp3FullProfile() {
        // Given/When
        var profile = Http3Profiles.serverHttp3Full();

        // Then
        assertThat(profile.getName()).isEqualTo("SERVER_HTTP3_FULL");
        assertThat(profile.isEnabled("http3")).isTrue();
        assertThat(profile.getEnabledCategories()).contains(
                HttpFeatureCategory.CORE,
                HttpFeatureCategory.TRANSFER,
                HttpFeatureCategory.CONTENT,
                HttpFeatureCategory.CACHING,
                HttpFeatureCategory.CONNECTION,
                HttpFeatureCategory.ENTITY,
                HttpFeatureCategory.METADATA,
                HttpFeatureCategory.SECURITY,
                HttpFeatureCategory.HTTP3
        );
    }

    @Test
    void testServerProfileHasCoreFeature() {
        // Given/When
        var profile = Http3Profiles.serverHttp3();
        var coreFeature = profile.getFeature("core");

        // Then
        assertThat(coreFeature).isNotNull();
        assertThat(coreFeature.isCore()).isTrue();
    }

    @Test
    void testHttp3FeatureIsNotCore() {
        // Given/When
        var profile = Http3Profiles.serverHttp3();
        var h3Feature = profile.getFeature("http3");

        // Then
        assertThat(h3Feature).isNotNull();
        assertThat(h3Feature.isCore()).isFalse();
    }
}
