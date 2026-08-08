package ssg.legoflow.ws.endpoint;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous wrapper around a synchronous {@link EndpointInvoker}.
 *
 * <p>Returns {@link CompletableFuture} for endpoint invocation, delegating to the
 * underlying sync implementation on JDK 25 virtual threads via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.
 *
 * <h2>Sync-Primary Design Rationale</h2>
 * <p>The sync {@link EndpointInvoker} is the primary implementation. Virtual
 * threads make blocking calls near-zero-cost, so sync APIs are simpler to write,
 * debug, and test. This async wrapper exists for callers who need
 * {@code CompletableFuture} composability when invoking endpoints as part
 * of larger async request-processing pipelines.</p>
 *
 * @see EndpointInvoker
 * @since 0.1.0
 */
public class AsyncEndpointInvoker {

    private final EndpointInvoker delegate;
    private final ExecutorService executor;

    /**
     * Creates an async wrapper around the given sync endpoint invoker.
     *
     * @param delegate the sync endpoint invoker to wrap
     */
    public AsyncEndpointInvoker(EndpointInvoker delegate) {
        this.delegate = delegate;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates an async wrapper with a custom executor.
     *
     * @param delegate the sync endpoint invoker to wrap
     * @param executor the executor for async dispatch
     */
    public AsyncEndpointInvoker(EndpointInvoker delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    /**
     * Asynchronously invokes the matching endpoint for the given request.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return a future completing with the HTTP response
     */
    public CompletableFuture<HttpResponse> invoke(HttpContext ctx, HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> delegate.invoke(ctx, request), executor);
    }

    /**
     * Returns the underlying synchronous endpoint invoker.
     *
     * @return the sync delegate
     */
    public EndpointInvoker sync() {
        return delegate;
    }
}
