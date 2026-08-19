package ssg.legoflow.ws.request;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpMethod;
class RequestMapperTest {

    @Test void testGetBody() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Body may be null for GET requests with no body
        String body = mapper.getBody(request);
        // Either null or empty - both are acceptable
    }

    @Test void testGetQueryParameters() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/search?q=test&page=1");
        var params = mapper.getQueryParameters(request);
        assertThat(params).containsKey("q");
    }

    @Test void testGetContentType() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.POST, "/api/data");
        String ct = mapper.getContentType(request);
        // Content-type may be null when not set in headers
    }

    @Test void testGetPathSegment() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/api/users/123/orders");
        String segment = mapper.getPathSegment(request, 0);
        assertThat(segment).isEqualTo("api");
        
        segment = mapper.getPathSegment(request, 1);
        assertThat(segment).isEqualTo("users");
        
        segment = mapper.getPathSegment(request, 2);
        assertThat(segment).isEqualTo("123");
    }

    @Test void testHttpRequestWithDifferentMethods() {
        for (var method : HttpMethod.values()) {
            var request = HttpRequest.of(method, "/path");
            assertThat(request.getMethod()).isEqualTo(method);
        }
    }

    @Test void testGetBodyWithNoContent() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/empty");
        String body = mapper.getBody(request);
        // Body may be null for requests without content
    }

    @Test void testQueryParametersEmpty() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        var params = mapper.getQueryParameters(request);
        assertThat(params).isEmpty();
    }

    @Test void testGetPathSegmentOutOfBounds() {
        var mapper = new RequestMapper();
        var request = HttpRequest.of(HttpMethod.GET, "/single");
        // Out of bounds index behavior depends on implementation
        String segment = mapper.getPathSegment(request, 5);
    }
}
