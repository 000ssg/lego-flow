package ssg.legoflow.http2.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http.feature.HttpFeatureCategory;

class Http2ProfilesTest {

    @Test void testServerHttp2Profile() {
        var profile = Http2Profiles.serverHttp2();
        assertThat(profile.getName()).isEqualTo("SERVER_HTTP2");
        assertThat(profile.getFeatures()).hasSizeGreaterThan(0);
        // Should include core, transfer, connection features
    }

    @Test void testClientHttp2Profile() {
        var profile = Http2Profiles.clientHttp2();
        assertThat(profile.getName()).isEqualTo("CLIENT_HTTP2");
        assertThat(profile.getFeatures()).hasSizeGreaterThan(0);
    }

    @Test void testServerHttp2FullProfile() {
        var profile = Http2Profiles.serverHttp2Full();
        assertThat(profile.getName()).isEqualTo("SERVER_HTTP2_FULL");
        // Full profile should have more features than basic
        var fullFeatures = profile.getFeatures().size();
        var basicFeatures = Http2Profiles.serverHttp2().getFeatures().size();
        assertThat(fullFeatures).isGreaterThanOrEqualTo(basicFeatures);
    }

    @Test void testProfilesAreIndependent() {
        var server = Http2Profiles.serverHttp2();
        var client = Http2Profiles.clientHttp2();
        var full = Http2Profiles.serverHttp2Full();
        
        // Each call should create a new independent feature set
        assertThat(server).isNotSameAs(client);
        assertThat(full).isNotSameAs(server);
    }

    @Test void testServerProfileContainsCoreFeature() {
        var profile = Http2Profiles.serverHttp2();
        boolean hasCore = profile.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("core"));
        assertThat(hasCore).isTrue();
    }

    @Test void testServerProfileContainsTransferFeature() {
        var profile = Http2Profiles.serverHttp2();
        boolean hasTransfer = profile.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("transfer"));
        assertThat(hasTransfer).isTrue();
    }

    @Test void testServerProfileContainsConnectionFeature() {
        var profile = Http2Profiles.serverHttp2();
        boolean hasConnection = profile.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("connection"));
        assertThat(hasConnection).isTrue();
    }

    @Test void testFullProfileHasExtraFeatures() {
        var full = Http2Profiles.serverHttp2Full();
        boolean hasContent = full.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("content-negotiation"));
        assertThat(hasContent).isTrue();
        
        boolean hasCaching = full.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("caching"));
        assertThat(hasCaching).isTrue();
    }

    @Test void testCoreFeatureIsCore() {
        var profile = Http2Profiles.serverHttp2();
        var coreFeature = profile.getFeatures().stream()
                .filter(f -> f.getName().equals("core"))
                .findFirst().orElse(null);
        assertThat(coreFeature).isNotNull();
        assertThat(coreFeature.isCore()).isTrue();
    }

    @Test void testNonCoreFeatureNotCore() {
        var profile = Http2Profiles.serverHttp2();
        var transferFeature = profile.getFeatures().stream()
                .filter(f -> f.getName().equals("transfer"))
                .findFirst().orElse(null);
        assertThat(transferFeature).isNotNull();
        assertThat(transferFeature.isCore()).isFalse();
    }

    @Test void testProfileFeatureCategories() {
        var profile = Http2Profiles.serverHttp2();
        for (var feature : profile.getFeatures()) {
            assertThat(feature.getCategory()).isNotNull();
        }
    }

    @Test void testCoreFeatureCategoryIsCORE() {
        var profile = Http2Profiles.serverHttp2();
        var coreFeature = profile.getFeatures().stream()
                .filter(f -> f.getName().equals("core"))
                .findFirst().orElse(null);
        assertThat(coreFeature.getCategory()).isEqualTo(HttpFeatureCategory.CORE);
    }

    @Test void testTransferFeatureCategoryIsTRANSFER() {
        var profile = Http2Profiles.serverHttp2();
        var transferFeature = profile.getFeatures().stream()
                .filter(f -> f.getName().equals("transfer"))
                .findFirst().orElse(null);
        assertThat(transferFeature.getCategory()).isEqualTo(HttpFeatureCategory.TRANSFER);
    }

    @Test void testServerProfileContainsHttp2Feature() {
        var profile = Http2Profiles.serverHttp2();
        boolean hasHttp2 = profile.getFeatures().stream()
                .anyMatch(f -> f.getName().equals("http2"));
        assertThat(hasHttp2).isTrue();
    }

    @Test void testConfigureDoesNotThrow() {
        var profile = Http2Profiles.serverHttp2();
        for (var feature : profile.getFeatures()) {
            // Configure with empty params should not throw
            assertThatNoException().isThrownBy(() -> 
                feature.configure(java.util.Map.of()));
        }
    }
}
