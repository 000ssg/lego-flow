package ssg.legoflow.http.feature;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HttpFeatureRegistry {

    private final Map<String, HttpFeature> features = new ConcurrentHashMap<>();

    public void register(HttpFeature feature) {
        features.put(feature.getName(), feature);
    }

    public void unregister(String name) {
        var feature = features.get(name);
        if (feature != null && !feature.isCore()) {
            features.remove(name);
        }
    }

    public HttpFeature getFeature(String name) {
        return features.get(name);
    }

    public List<HttpFeature> getFeatures() {
        return List.copyOf(features.values());
    }

    public List<HttpFeature> getByCategory(HttpFeatureCategory category) {
        return features.values().stream()
                .filter(f -> f.getCategory() == category)
                .toList();
    }

    public boolean isEnabled(String name) {
        return features.containsKey(name);
    }

    public Set<String> getFeatureNames() {
        return Set.copyOf(features.keySet());
    }
}
