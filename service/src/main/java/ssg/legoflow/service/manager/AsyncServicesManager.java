package ssg.legoflow.service.manager;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncServicesManager implements AutoCloseable {

    private final ServicesManager delegate;
    private final ExecutorService executor;

    public AsyncServicesManager(ServicesManager delegate) {
        this(delegate, Executors.newVirtualThreadPerTaskExecutor());
    }

    public AsyncServicesManager(ServicesManager delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    public CompletableFuture<Void> register(Service<?, ?> service) {
        return CompletableFuture.runAsync(() -> delegate.register(service), executor);
    }

    public CompletableFuture<Void> unregister(String serviceName) {
        return CompletableFuture.runAsync(() -> delegate.unregister(serviceName), executor);
    }

    public CompletableFuture<Service<?, ?>> getService(String name) {
        return CompletableFuture.supplyAsync(() -> delegate.getService(name), executor);
    }

    public CompletableFuture<List<Service<?, ?>>> getServices() {
        return CompletableFuture.supplyAsync(delegate::getServices, executor);
    }

    public CompletableFuture<Void> startAll() {
        return CompletableFuture.runAsync(delegate::startAll, executor);
    }

    public CompletableFuture<Void> stopAll() {
        return CompletableFuture.runAsync(delegate::stopAll, executor);
    }

    public CompletableFuture<Void> pauseAll() {
        return CompletableFuture.runAsync(delegate::pauseAll, executor);
    }

    public CompletableFuture<Void> resumeAll() {
        return CompletableFuture.runAsync(delegate::resumeAll, executor);
    }

    public CompletableFuture<Void> start(String serviceName) {
        return CompletableFuture.runAsync(() -> delegate.start(serviceName), executor);
    }

    public CompletableFuture<Void> stop(String serviceName) {
        return CompletableFuture.runAsync(() -> delegate.stop(serviceName), executor);
    }

    public CompletableFuture<Map<String, ProcessorState>> getStates() {
        return CompletableFuture.supplyAsync(delegate::getStates, executor);
    }

    public ServicesManager sync() {
        return delegate;
    }

    @Override
    public void close() {
        delegate.close();
        executor.close();
    }
}
