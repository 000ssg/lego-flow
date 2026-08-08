package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.service.AsyncService;
import ssg.legoflow.service.ServiceContext;

import java.util.concurrent.CompletableFuture;

/**
 * Simplest async demo: wraps {@link EchoService} with a {@link CompletableFuture}-based API
 * via the default {@code async()} bridge on {@code Service}.
 *
 * @since 0.1
 */
public class AsyncEchoService {

    private final EchoService echo;
    private final AsyncService<String, String> async;

    public AsyncEchoService() {
        this.echo = new EchoService();
        this.async = echo.async();
    }

    public CompletableFuture<Void> connect(ServiceContext ctx) {
        return async.connect(ctx);
    }

    public CompletableFuture<Void> disconnect(ServiceContext ctx) {
        return async.disconnect(ctx);
    }

    public CompletableFuture<Void> send(ServiceContext ctx, String... messages) {
        return async.consume(ctx, messages);
    }

    public CompletableFuture<Void> reply(ServiceContext ctx, String... messages) {
        return async.submit(ctx, messages);
    }

    public EchoService getEcho() {
        return echo;
    }

    public AsyncService<String, String> getAsync() {
        return async;
    }
}
