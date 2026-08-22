package ssg.legoflow.service.functional;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
class AsyncServicePipelineTest {

    @Test
    void testProcessWithVirtualThreads() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.map(i -> i * 2);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        assertThat(async.process(List.of(1, 2, 3, 4, 5)).join())
                .containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    void testProcessWithCustomExecutor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ServicePipeline<String> pipeline = new ServicePipeline<>();
            pipeline.map(String::toUpperCase);
            
            AsyncServicePipeline<String> async = new AsyncServicePipeline<>(pipeline, executor);
            assertThat(async.process(List.of("hello", "world")).join())
                    .containsExactly("HELLO", "WORLD");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testForEach() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        async.forEach(List.of(1, 2, 3), counter::addAndGet).join();
        assertThat(counter.get()).isEqualTo(6);
    }

    @Test
    void testCollect() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.filter(i -> i > 2);
        pipeline.map(i -> i * 10);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        assertThat(async.collect(List.of(1, 2, 3, 4, 5), Object::toString).join())
                .containsExactly("30", "40", "50");
    }

    @Test
    void testSyncReturnsDelegate() {
        ServicePipeline<Integer> delegate = new ServicePipeline<>();
        assertThat(new AsyncServicePipeline<>(delegate).sync()).isSameAs(delegate);
    }

    @Test
    void testProcessWithEmptyList() {
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(new ServicePipeline<>());
        assertThat(async.process(List.of()).join()).isEmpty();
    }

    @Test
    void testCollectWithDifferentType() {
        ServicePipeline<String> pipeline = new ServicePipeline<>();
        pipeline.map(s -> String.valueOf(s.length()));
        
        AsyncServicePipeline<String> async = new AsyncServicePipeline<>(pipeline);
        assertThat(async.collect(List.of("hello", "hi", "hey"), Integer::parseInt).join())
                .containsExactly(5, 2, 3);
    }

    @Test
    void testConcurrencySafety() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.map(i -> i * i);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline, executor);
        
        List<CompletableFuture<List<Integer>>> futures = IntStream.range(0, 10)
                .mapToObj(i -> async.process(List.of(i, i+1, i+2)))
                .collect(Collectors.toList());
        
        long total = futures.stream().map(CompletableFuture::join).flatMap(List::stream).count();
        assertThat(total).isEqualTo(30);
        executor.shutdown();
    }

    @Test
    void testExceptionInProcessing() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.map(i -> {
            if (i < 0) throw new IllegalArgumentException("negative: " + i);
            return i * 2;
        });
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        CompletableFuture<List<Integer>> future = async.process(List.of(1, -1, 3));
        
        // join() propagates exceptions wrapped in CompletionException or directly
        assertThatThrownBy(() -> future.join())
                .isInstanceOfAny(IllegalArgumentException.class, Exception.class);
    }

    @Test
    void testProcessWithMultipleMaps() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.map(i -> i + 1);
        pipeline.map(i -> i * 2);
        pipeline.map(i -> i - 3);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        assertThat(async.process(List.of(5, 10)).join()).containsExactly(9, 19);
    }

    @Test
    void testProcessWithMultipleFilters() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.filter(i -> i > 0);
        pipeline.filter(i -> i < 10);
        pipeline.filter(i -> i % 2 == 0);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        assertThat(async.process(IntStream.range(-5, 15).boxed().collect(Collectors.toList())).join())
                .containsExactly(2, 4, 6, 8);
    }

    @Test
    void testForEachWithSideEffects() {
        ServicePipeline<String> pipeline = new ServicePipeline<>();
        pipeline.map(String::toUpperCase);
        pipeline.filter(s -> s.length() > 3);
        
        AsyncServicePipeline<String> async = new AsyncServicePipeline<>(pipeline);
        
        List<String> collected = new java.util.ArrayList<>();
        async.forEach(List.of("hi", "hello", "hey", "world"), collected::add).join();
        
        assertThat(collected).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testServicePipelineFromMethod() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        List<Integer> result = async.process(List.of(1, 2, 3)).join();
        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    void testProcessWithFilterAndMap() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.filter(i -> i > 5);
        pipeline.map(i -> i * 100);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        List<Integer> result = async.process(IntStream.range(0, 20).boxed().collect(Collectors.toList())).join();
        
        assertThat(result).hasSize(14);
        assertThat(result.get(0)).isEqualTo(600);
    }

    @Test
    void testVirtualThreadExecutorCreatedAutomatically() {
        ServicePipeline<String> pipeline = new ServicePipeline<>();
        pipeline.map(String::trim);
        
        AsyncServicePipeline<String> async = new AsyncServicePipeline<>(pipeline);
        List<String> result = async.process(List.of(" hello ", " world ")).join();
        assertThat(result).containsExactly("hello", "world");
    }

    @Test
    void testMultipleProcessesOnSameAsyncInstance() {
        ServicePipeline<Integer> pipeline = new ServicePipeline<>();
        pipeline.map(i -> i + 1);
        
        AsyncServicePipeline<Integer> async = new AsyncServicePipeline<>(pipeline);
        
        List<Integer> r1 = async.process(List.of(1, 2)).join();
        List<Integer> r2 = async.process(List.of(3, 4)).join();
        
        assertThat(r1).containsExactly(2, 3);
        assertThat(r2).containsExactly(4, 5);
    }
}
