package ssg.legoflow.http3.config;

import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http3.feature.Http3Feature;

import java.util.Map;

/**
 * Standard HTTP/3 feature profiles for server and client configurations.
 *
 * @since 1.0.0
 */
public class Http3Profiles {

    /**
     * Standard server HTTP/3 profile with core, transfer, connection, and HTTP/3 features.
     *
     * @return the server feature set
     * @since 1.0.0
     */
    public static HttpFeatureSet serverHttp3() {
        return new HttpFeatureSet("SERVER_HTTP3")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(new Http3Feature());
    }

    /**
     * Standard client HTTP/3 profile with core, transfer, connection, and HTTP/3 features.
     *
     * @return the client feature set
     * @since 1.0.0
     */
    public static HttpFeatureSet clientHttp3() {
        return new HttpFeatureSet("CLIENT_HTTP3")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(new Http3Feature());
    }

    /**
     * Full server HTTP/3 profile with all feature categories enabled.
     *
     * @return the full server feature set
     * @since 1.0.0
     */
    public static HttpFeatureSet serverHttp3Full() {
        return new HttpFeatureSet("SERVER_HTTP3_FULL")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("caching", HttpFeatureCategory.CACHING))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA))
                .add(simpleFeature("security", HttpFeatureCategory.SECURITY))
                .add(new Http3Feature());
    }

    private static SimpleFeature coreFeature() {
        return new SimpleFeature("core", HttpFeatureCategory.CORE, true);
    }

    private static SimpleFeature simpleFeature(String name, HttpFeatureCategory category) {
        return new SimpleFeature(name, category, false);
    }

    private static class SimpleFeature implements HttpFeature {
        private final String name;
        private final HttpFeatureCategory category;
        private final boolean core;

        SimpleFeature(String name, HttpFeatureCategory category, boolean core) {
            this.name = name;
            this.category = category;
            this.core = core;
        }

        @Override public String getName() { return name; }
        @Override public HttpFeatureCategory getCategory() { return category; }
        @Override public boolean isCore() { return core; }
        @Override public void configure(Map<String, Object> params) {}
        @Override public void install(HttpFeatureRegistry registry) {
            registry.register(this);
        }
    }
}
