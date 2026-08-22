package ssg.legoflow.ws.endpoint;

import ssg.legoflow.http.core.*;
import java.util.List;
public class EndpointInvoker {

    private final List<Endpoint> endpoints;

    public EndpointInvoker(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public HttpResponse invoke(HttpContext ctx, HttpRequest request) {
        var path = request.getUri().contains("?")
                ? request.getUri().substring(0, request.getUri().indexOf('?'))
                : request.getUri();
        for (var endpoint : endpoints) {
            if (endpoint.path().equals(path) && endpoint.method() == request.getMethod()) {
                return endpoint.handler().handle(ctx, request);
            }
        }
        return HttpResponse.of(HttpStatus.NOT_FOUND, "Endpoint not found");
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }
}
