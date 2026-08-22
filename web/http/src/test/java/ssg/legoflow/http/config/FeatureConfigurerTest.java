package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FeatureConfigurerTest {

    @Test
    void testEnableFeatureAddsToSet() {
        var featureSet = StandardProfiles.serverMinimal();
        var configurer = new FeatureConfigurer(featureSet);

        assertThat(featureSet.isEnabled("security")).isFalse();

        configurer.enable(new TestFeature("security", HttpFeatureCategory.SECURITY));

        assertThat(featureSet.isEnabled("security")).isTrue();
    }

    @Test
    void testDisableFeatureRemovesFromSet() {
        var featureSet = StandardProfiles.serverStandard();
        var configurer = new FeatureConfigurer(featureSet);

        assertThat(featureSet.isEnabled("caching")).isTrue();

        configurer.disable("caching");

        assertThat(featureSet.isEnabled("caching")).isFalse();
    }

    @Test
    void testDisableCoreFeatureIsIgnored() {
        var featureSet = StandardProfiles.serverMinimal();
        var configurer = new FeatureConfigurer(featureSet);

        assertThat(featureSet.isEnabled("core")).isTrue();

        configurer.disable("core");

        assertThat(featureSet.isEnabled("core")).isTrue();
    }

    @Test
    void testConfigureFeature() {
        var featureSet = StandardProfiles.serverStandard();
        var configurer = new FeatureConfigurer(featureSet);

        configurer.configure("caching", "max-age", 7200);

        assertThat(featureSet.getFeature("caching")).isNotNull();
    }

    @Test
    void testConfigureNonExistentFeatureIsNoOp() {
        var featureSet = StandardProfiles.serverMinimal();
        var configurer = new FeatureConfigurer(featureSet);

        assertThatNoException().isThrownBy(() ->
                configurer.configure("nonexistent", "key", "value"));
    }

    @Test
    void testFluentChaining() {
        var featureSet = StandardProfiles.serverMinimal();
        var result = new FeatureConfigurer(featureSet)
                .enable(new TestFeature("security", HttpFeatureCategory.SECURITY))
                .enable(new TestFeature("websocket", HttpFeatureCategory.WEBSOCKET))
                .disable("websocket")
                .build();

        assertThat(result.isEnabled("security")).isTrue();
        assertThat(result.isEnabled("websocket")).isFalse();
        assertThat(result.isEnabled("core")).isTrue();
    }

    @Test
    void testBuildReturnsFeatureSet() {
        var featureSet = StandardProfiles.serverMinimal();
        var configurer = new FeatureConfigurer(featureSet);

        var result = configurer.build();

        assertThat(result).isSameAs(featureSet);
    }

    private static class TestFeature implements ssg.legoflow.http.feature.HttpFeature {
        private final String name;
        private final HttpFeatureCategory category;

        TestFeature(String name, HttpFeatureCategory category) {
            this.name = name;
            this.category = category;
        }

        @Override public String getName() { return name; }
        @Override public HttpFeatureCategory getCategory() { return category; }
        @Override public boolean isCore() { return false; }
        @Override public void configure(java.util.Map<String, Object> params) {}
        @Override public void install(ssg.legoflow.http.feature.HttpFeatureRegistry registry) {
            registry.register(this);
        }
    }
}
