package ssg.legoflow.ws;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous wrapper around a synchronous {@link WebServiceRegistry}.
 *
 * <p>Returns {@link CompletableFuture} for every operation, delegating to the
 * underlying sync implementation on JDK 25 virtual threads via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.
 *
 * <h2>Sync-Primary Design Rationale</h2>
 * <p>The sync {@link WebServiceRegistry} is the primary implementation. Virtual
 * threads make blocking calls near-zero-cost, so sync APIs are simpler to write,
 * debug, and test. This async wrapper exists for callers who need
 * {@code CompletableFuture} composability when performing service discovery
 * or registration as part of larger async workflows.</p>
 *
 * @see WebServiceRegistry
 * @since 0.1.0
 */
public class AsyncWebServiceRegistry {

    private final WebServiceRegistry delegate;
    private final ExecutorService executor;

    /**
     * Creates an async wrapper around the given sync registry.
     *
     * @param delegate the sync registry to wrap
     */
    public AsyncWebServiceRegistry(WebServiceRegistry delegate) {
        this.delegate = delegate;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates an async wrapper with a custom executor.
     *
     * @param delegate the sync registry to wrap
     * @param executor the executor for async dispatch
     */
    public AsyncWebServiceRegistry(WebServiceRegistry delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    /**
     * Asynchronously registers a web service.
     *
     * @param service the web service to register
     * @return a future completing when registration is done
     */
    public CompletableFuture<Void> register(WebService service) {
        return CompletableFuture.runAsync(() -> delegate.register(service), executor);
    }

    /**
     * Asynchronously unregisters a web service by path.
     *
     * @param path the service path to unregister
     * @return a future completing when unregistration is done
     */
    public CompletableFuture<Void> unregister(String path) {
        return CompletableFuture.runAsync(() -> delegate.unregister(path), executor);
    }

    /**
     * Asynchronously looks up a web service by path.
     *
     * @param path the service path
     * @return a future completing with the web service, or null if not found
     */
    public CompletableFuture<WebService> getService(String path) {
        return CompletableFuture.supplyAsync(() -> delegate.getService(path), executor);
    }

    /**
     * Asynchronously retrieves all registered services.
     *
     * @return a future completing with an immutable list of registered services
     */
    public CompletableFuture<List<WebService>> getServices() {
        return CompletableFuture.supplyAsync(delegate::getServices, executor);
    }

    /**
     * Returns the underlying synchronous registry.
     *
     * @return the sync delegate
     */
    public WebServiceRegistry sync() {
        return delegate;
    }
}
