package ssg.legoflow.service.demo.functional;

import ssg.legoflow.service.functional.AsyncServicePipeline;
import ssg.legoflow.service.functional.ServicePipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Async pipeline demo: wraps a sync {@link ServicePipeline} with
 * {@link AsyncServicePipeline} for {@link CompletableFuture}-based processing.
 *
 * @param <T> element type
 * @since 0.1
 */
public class AsyncPipelineDemo<T> {

    private final ServicePipeline<T> syncPipeline;
    private final AsyncServicePipeline<T> asyncPipeline;

    public AsyncPipelineDemo(ServicePipeline<T> syncPipeline) {
        this.syncPipeline = syncPipeline;
        this.asyncPipeline = new AsyncServicePipeline<>(syncPipeline);
    }

    public CompletableFuture<List<T>> process(List<T> data) {
        return asyncPipeline.process(data);
    }

    public CompletableFuture<Void> forEach(List<T> data, Consumer<T> action) {
        return asyncPipeline.forEach(data, action);
    }

    public <R> CompletableFuture<List<R>> collect(List<T> data, Function<T, R> collector) {
        return asyncPipeline.collect(data, collector);
    }

    public ServicePipeline<T> getSyncPipeline() {
        return syncPipeline;
    }

    public AsyncServicePipeline<T> getAsyncPipeline() {
        return asyncPipeline;
    }

    public static AsyncPipelineDemo<String> stringUpperCase() {
        var pipeline = new ServicePipeline<String>()
                .filter(s -> s != null && !s.isEmpty())
                .map(String::toUpperCase);
        return new AsyncPipelineDemo<>(pipeline);
    }

    public static AsyncPipelineDemo<Integer> positiveOnly() {
        var pipeline = new ServicePipeline<Integer>().filter(i -> i > 0);
        return new AsyncPipelineDemo<>(pipeline);
    }
}
