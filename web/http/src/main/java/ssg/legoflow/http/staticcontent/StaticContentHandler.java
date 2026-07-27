package ssg.legoflow.http.staticcontent;

import ssg.legoflow.http.caching.CacheControl;
import ssg.legoflow.http.core.*;

import java.nio.charset.StandardCharsets;

public class StaticContentHandler {

    private final ContentResolver resolver;
    private final StaticContentConfig config;

    public StaticContentHandler(ContentResolver resolver) {
        this(resolver, new StaticContentConfig());
    }

    public StaticContentHandler(ContentResolver resolver, StaticContentConfig config) {
        this.resolver = resolver;
        this.config = config;
    }

    public HttpResponse handle(HttpRequest request) {
        var path = request.getUri();
        var prefix = config.getUrlPrefix();
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }

        var resolved = resolver.resolve(path);
        if (resolved.isEmpty()) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, "Not Found");
        }

        var content = resolved.get();
        var response = HttpResponse.of(HttpStatus.OK);
        response.setBody(content.content());
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, content.mediaType().toString());
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.content().remaining()));

        var cacheControl = new CacheControl().setPublic(true).maxAge(config.getCacheMaxAge());
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, cacheControl.toString());

        return response;
    }

    public ContentResolver getResolver() { return resolver; }
    public StaticContentConfig getConfig() { return config; }
}
