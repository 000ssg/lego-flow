package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureSet;

import java.util.Map;

public class StandardProfiles {

    public static HttpFeatureSet serverMinimal() {
        return new HttpFeatureSet("SERVER_MINIMAL")
                .add(coreFeature())
                .add(simpleFeature("fixed-length-transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("connection-basic", HttpFeatureCategory.CONNECTION));
    }

    public static HttpFeatureSet serverStandard() {
        return new HttpFeatureSet("SERVER_STANDARD")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("caching", HttpFeatureCategory.CACHING))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA));
    }

    public static HttpFeatureSet serverFull() {
        return new HttpFeatureSet("SERVER_FULL")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("caching", HttpFeatureCategory.CACHING))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA))
                .add(simpleFeature("security", HttpFeatureCategory.SECURITY))
                .add(simpleFeature("websocket", HttpFeatureCategory.WEBSOCKET))
                .add(simpleFeature("static-content", HttpFeatureCategory.STATIC));
    }

    public static HttpFeatureSet clientMinimal() {
        return new HttpFeatureSet("CLIENT_MINIMAL")
                .add(coreFeature())
                .add(simpleFeature("fixed-length-transfer", HttpFeatureCategory.TRANSFER));
    }

    public static HttpFeatureSet clientStandard() {
        return new HttpFeatureSet("CLIENT_STANDARD")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA));
    }

    public static HttpFeatureSet clientFull() {
        return new HttpFeatureSet("CLIENT_FULL")
                .add(coreFeature())
                .add(simpleFeature("transfer", HttpFeatureCategory.TRANSFER))
                .add(simpleFeature("content-negotiation", HttpFeatureCategory.CONTENT))
                .add(simpleFeature("connection", HttpFeatureCategory.CONNECTION))
                .add(simpleFeature("entity", HttpFeatureCategory.ENTITY))
                .add(simpleFeature("metadata", HttpFeatureCategory.METADATA))
                .add(simpleFeature("security", HttpFeatureCategory.SECURITY))
                .add(simpleFeature("websocket", HttpFeatureCategory.WEBSOCKET));
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
