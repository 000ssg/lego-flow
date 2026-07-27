package ssg.legoflow.ws;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous wrapper around a synchronous {@link WebService}.
 *
 * <p>Returns {@link CompletableFuture} for every operation, delegating to the
 * underlying sync implementation on JDK 25 virtual threads via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.
 *
 * <h2>Sync-Primary Design Rationale</h2>
 * <p>The sync {@link WebService} is the primary implementation. JDK 25 virtual
 * threads make blocking calls near-zero-cost, so sync APIs are simpler to write,
 * debug, and test. This async wrapper exists solely for callers who need
 * {@code CompletableFuture} composability (e.g., combining web service results
 * with other async operations, building reactive pipelines, or integrating with
 * frameworks that expect {@code CompletableFuture}-based APIs).</p>
 *
 * @see WebService
 * @since 1.0.0
 */
public class AsyncWebService {

    private final WebService delegate;
    private final ExecutorService executor;

    /**
     * Creates an async wrapper around the given sync web service.
     *
     * @param delegate the sync web service to wrap
     */
    public AsyncWebService(WebService delegate) {
        this.delegate = delegate;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates an async wrapper with a custom executor.
     *
     * @param delegate the sync web service to wrap
     * @param executor the executor for async dispatch
     */
    public AsyncWebService(WebService delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    /**
     * Asynchronously handles an HTTP request through the web service pipeline.
     *
     * @param ctx     the web service context
     * @param request the HTTP request
     * @return a future completing with the HTTP response
     */
    public CompletableFuture<HttpResponse> handle(WebServiceContext ctx, HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> delegate.handle(ctx, request), executor);
    }

    /**
     * Returns the descriptor of the underlying web service.
     *
     * @return a future completing with the service descriptor
     */
    public CompletableFuture<WebServiceDescriptor> getDescriptor() {
        return CompletableFuture.supplyAsync(delegate::getDescriptor, executor);
    }

    /**
     * Returns the underlying synchronous web service.
     *
     * @return the sync delegate
     */
    public WebService sync() {
        return delegate;
    }
}
