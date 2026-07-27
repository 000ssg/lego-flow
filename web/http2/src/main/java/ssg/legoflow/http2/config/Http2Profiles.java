package ssg.legoflow.http2.config;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http2.feature.Http2Feature;

import java.util.Map;

public class Http2Profiles {

    public static HttpFeatureSet serverHttp2() {
        return new HttpFeatureSet("SERVER_HTTP2")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(new Http2Feature());
    }

    public static HttpFeatureSet clientHttp2() {
        return new HttpFeatureSet("CLIENT_HTTP2")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(new Http2Feature());
    }

    public static HttpFeatureSet serverHttp2Full() {
        return new HttpFeatureSet("SERVER_HTTP2_FULL")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("caching", HttpFeatureCategory.CACHING))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA))
                .add(simpleFeature("security", HttpFeatureCategory.SECURITY))
                .add(new Http2Feature());
    }

    private static SimpleFeature coreFeature() {
        return new SimpleFeature("core", HttpFeatureCategory.CORE, true);
    }

    private static SimpleFeature simpleFeature(String name, HttpFeatureCategory category) {
        return new SimpleFeature(name, category, false);
    }

    private static class SimpleFeature implements ssg.legoflow.http.feature.HttpFeature {
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
        @Override public void install(ssg.legoflow.http.feature.HttpFeatureRegistry registry) {
            registry.register(this);
        }
    }
}
