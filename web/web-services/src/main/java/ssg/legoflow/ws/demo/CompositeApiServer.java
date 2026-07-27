package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.WebServiceRegistry;

import java.util.Set;

public class CompositeApiServer {

    private final HttpRouter router;
    private final WebServiceRegistry registry;
    private final TodoApiService todoService;
    private final UserApiService userService;

    public CompositeApiServer() {
        this.router = new HttpRouter();
        this.registry = new WebServiceRegistry();
        this.todoService = new TodoApiService();
        this.userService = new UserApiService();

        registry.register(todoService);
        registry.register(userService);
        registry.installRoutes(router);

        router.get("/health", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "{\"status\":\"UP\"}"));
    }

    public HttpResponse dispatch(HttpContext ctx, HttpRequest request) {
        return router.dispatch(ctx, request);
    }

    public HttpRouter getRouter() { return router; }

    public TodoApiService getTodoService() { return todoService; }

    public UserApiService getUserService() { return userService; }
}
