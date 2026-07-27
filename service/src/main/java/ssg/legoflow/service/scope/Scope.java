package ssg.legoflow.service.scope;

import java.util.Map;

public interface Scope {

    String getId();

    <T> T getAttribute(String key);

    void setAttribute(String key, Object value);

    Map<String, Object> getAttributes();

    void destroy();
}
