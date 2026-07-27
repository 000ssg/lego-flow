package ssg.legoflow.http.core;

@FunctionalInterface
public interface HttpRequestHandler {

    HttpResponse handle(HttpContext ctx, HttpRequest request);
}
