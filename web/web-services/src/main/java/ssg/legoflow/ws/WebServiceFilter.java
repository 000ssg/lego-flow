package ssg.legoflow.ws;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.core.HttpRequest;

import java.util.function.Predicate;

public class WebServiceFilter extends AbstractDataFilter<HttpRequest> {

    private final Predicate<HttpRequest> predicate;

    public WebServiceFilter(Predicate<HttpRequest> predicate) {
        super(HttpRequest.class);
        this.predicate = predicate;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected HttpRequest[] doFilter(Context ctx, HttpRequest... data) {
        var filtered = new java.util.ArrayList<HttpRequest>();
        for (var req : data) {
            if (predicate.test(req)) {
                filtered.add(req);
            }
        }
        return filtered.toArray(HttpRequest[]::new);
    }

    public static WebServiceFilter byPath(String pathPrefix) {
        return new WebServiceFilter(req -> req.getUri().startsWith(pathPrefix));
    }

    public static WebServiceFilter byMethod(ssg.legoflow.http.core.HttpMethod method) {
        return new WebServiceFilter(req -> req.getMethod() == method);
    }

    public static WebServiceFilter byContentType(String contentType) {
        return new WebServiceFilter(req -> {
            var ct = req.getHeaders().get(ssg.legoflow.http.core.HttpHeaders.CONTENT_TYPE);
            return ct != null && ct.contains(contentType);
        });
    }
}
