package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.ws.WebService;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import ssg.legoflow.ws.content.JsonCodec;
import ssg.legoflow.ws.request.ResponseMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TodoApiService implements WebService {

    private final WebServiceDescriptor descriptor = new WebServiceDescriptor(
            "/todos", Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE));

    private final Map<String, Map<String, String>> todos = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final JsonCodec jsonCodec = new JsonCodec();
    private final ResponseMapper responseMapper = new ResponseMapper();

    @Override
    public WebServiceDescriptor getDescriptor() { return descriptor; }

    @Override
    public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
        return switch (request.getMethod()) {
            case GET -> handleGet(request);
            case POST -> handlePost(request);
            case PUT -> handlePut(request);
            case DELETE -> handleDelete(request);
            default -> HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
        };
    }

    private HttpResponse handleGet(HttpRequest request) {
        var items = new ArrayList<>(todos.values());
        return responseMapper.json(HttpStatus.OK, jsonCodec.encodeList(items));
    }

    private HttpResponse handlePost(HttpRequest request) {
        var body = request.getBodyAsString();
        var data = jsonCodec.decode(body);
        var id = String.valueOf(idCounter.incrementAndGet());
        data.put("id", id);
        todos.put(id, data);
        return responseMapper.json(HttpStatus.CREATED, jsonCodec.encode(data));
    }

    private HttpResponse handlePut(HttpRequest request) {
        var body = request.getBodyAsString();
        var data = jsonCodec.decode(body);
        var id = data.get("id");
        if (id == null || !todos.containsKey(id)) {
            return responseMapper.notFound("Todo not found");
        }
        todos.put(id, data);
        return responseMapper.json(HttpStatus.OK, jsonCodec.encode(data));
    }

    private HttpResponse handleDelete(HttpRequest request) {
        var params = request.getQueryParams();
        var id = params.get("id");
        if (id == null || !todos.containsKey(id)) {
            return responseMapper.notFound("Todo not found");
        }
        todos.remove(id);
        return responseMapper.noContent();
    }

    public int getTodoCount() { return todos.size(); }
}
