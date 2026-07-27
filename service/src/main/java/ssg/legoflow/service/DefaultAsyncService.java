package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.blocks.ProcessorStatistics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultAsyncService<I, O> implements AsyncService<I, O> {

    private final Service<I, O> delegate;
    private final ExecutorService executor;

    public DefaultAsyncService(Service<I, O> delegate) {
        this(delegate, Executors.newVirtualThreadPerTaskExecutor());
    }

    public DefaultAsyncService(Service<I, O> delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<Void> consume(ServiceContext ctx, I... data) {
        return CompletableFuture.runAsync(() -> delegate.consume(ctx, data), executor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<Void> submit(ServiceContext ctx, O... data) {
        return CompletableFuture.runAsync(() -> delegate.submit(ctx, data), executor);
    }

    @Override
    public CompletableFuture<Void> connect(ServiceContext ctx) {
        return CompletableFuture.runAsync(() -> delegate.connect(ctx), executor);
    }

    @Override
    public CompletableFuture<Void> disconnect(ServiceContext ctx) {
        return CompletableFuture.runAsync(() -> delegate.disconnect(ctx), executor);
    }

    @Override
    public ProcessorState getState() {
        return delegate.getState();
    }

    @Override
    public ProcessorStatistics getStatistics() {
        return delegate.getStatistics();
    }

    @Override
    public Service<I, O> sync() {
        return delegate;
    }
}
