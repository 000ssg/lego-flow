package ssg.legoflow.ws.request;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;

import java.util.Map;

public class RequestMapper {

    public String getBody(HttpRequest request) {
        return request.getBodyAsString();
    }

    public Map<String, String> getQueryParameters(HttpRequest request) {
        return request.getQueryParams();
    }

    public String getContentType(HttpRequest request) {
        return request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
    }

    public String getPathSegment(HttpRequest request, int index) {
        var path = request.getUri();
        if (path.contains("?")) path = path.substring(0, path.indexOf('?'));
        var segments = path.split("/");
        var nonEmpty = java.util.Arrays.stream(segments).filter(s -> !s.isEmpty()).toList();
        if (index < 0 || index >= nonEmpty.size()) return null;
        return nonEmpty.get(index);
    }
}
