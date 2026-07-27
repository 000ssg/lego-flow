package ssg.legoflow.service.scope;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractScope implements Scope {

    private final String id;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    protected AbstractScope() {
        this(UUID.randomUUID().toString());
    }

    protected AbstractScope(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }

    @Override
    public void destroy() {
        attributes.clear();
    }
}
