package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.ws.WebService;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import ssg.legoflow.ws.content.JsonCodec;
import ssg.legoflow.ws.request.ResponseMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserApiService implements WebService {

    private final WebServiceDescriptor descriptor = new WebServiceDescriptor(
            "/users", Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE));

    private final Map<String, Map<String, String>> users = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final JsonCodec jsonCodec = new JsonCodec();
    private final ResponseMapper responseMapper = new ResponseMapper();

    @Override
    public WebServiceDescriptor getDescriptor() { return descriptor; }

    @Override
    public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
        return switch (request.getMethod()) {
            case GET -> handleGet(request);
            case POST -> handlePost(ctx, request);
            case PUT -> handlePut(ctx, request);
            case DELETE -> handleDelete(ctx, request);
            default -> HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
        };
    }

    private HttpResponse handleGet(HttpRequest request) {
        var params = request.getQueryParams();
        var id = params.get("id");
        if (id != null) {
            var user = users.get(id);
            if (user == null) return responseMapper.notFound("User not found");
            return responseMapper.json(HttpStatus.OK, jsonCodec.encode(user));
        }
        var items = new ArrayList<>(users.values());
        return responseMapper.json(HttpStatus.OK, jsonCodec.encodeList(items));
    }

    private HttpResponse handlePost(WebServiceContext ctx, HttpRequest request) {
        if (!isAuthenticated(ctx)) {
            return responseMapper.json(HttpStatus.UNAUTHORIZED, "{\"error\":\"Authentication required\"}");
        }
        var body = request.getBodyAsString();
        var data = jsonCodec.decode(body);
        var id = String.valueOf(idCounter.incrementAndGet());
        data.put("id", id);
        users.put(id, data);
        return responseMapper.json(HttpStatus.CREATED, jsonCodec.encode(data));
    }

    private HttpResponse handlePut(WebServiceContext ctx, HttpRequest request) {
        if (!isAuthenticated(ctx)) {
            return responseMapper.json(HttpStatus.UNAUTHORIZED, "{\"error\":\"Authentication required\"}");
        }
        var body = request.getBodyAsString();
        var data = jsonCodec.decode(body);
        var id = data.get("id");
        if (id == null || !users.containsKey(id)) {
            return responseMapper.notFound("User not found");
        }
        users.put(id, data);
        return responseMapper.json(HttpStatus.OK, jsonCodec.encode(data));
    }

    private HttpResponse handleDelete(WebServiceContext ctx, HttpRequest request) {
        if (!isAuthenticated(ctx)) {
            return responseMapper.json(HttpStatus.UNAUTHORIZED, "{\"error\":\"Authentication required\"}");
        }
        var params = request.getQueryParams();
        var id = params.get("id");
        if (id == null || !users.containsKey(id)) {
            return responseMapper.notFound("User not found");
        }
        users.remove(id);
        return responseMapper.noContent();
    }

    private boolean isAuthenticated(WebServiceContext ctx) {
        var user = ctx.getUser();
        return user != null && user.hasRole(ServiceRole.USER);
    }

    public int getUserCount() { return users.size(); }
}
