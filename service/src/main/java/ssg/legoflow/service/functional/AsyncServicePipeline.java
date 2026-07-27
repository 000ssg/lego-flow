package ssg.legoflow.service.functional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class AsyncServicePipeline<T> {

    private final ServicePipeline<T> delegate;
    private final ExecutorService executor;

    public AsyncServicePipeline(ServicePipeline<T> delegate) {
        this(delegate, Executors.newVirtualThreadPerTaskExecutor());
    }

    public AsyncServicePipeline(ServicePipeline<T> delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    public CompletableFuture<List<T>> process(List<T> data) {
        return CompletableFuture.supplyAsync(() -> delegate.process(data), executor);
    }

    public CompletableFuture<Void> forEach(List<T> data, Consumer<T> action) {
        return CompletableFuture.runAsync(() -> delegate.forEach(data, action), executor);
    }

    public <R> CompletableFuture<List<R>> collect(List<T> data, Function<T, R> collector) {
        return CompletableFuture.supplyAsync(() -> delegate.collect(data, collector), executor);
    }

    public ServicePipeline<T> sync() {
        return delegate;
    }
}
