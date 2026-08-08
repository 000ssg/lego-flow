package ssg.legoflow.service.demo.functional;

import ssg.legoflow.service.functional.AsyncServicePipeline;
import ssg.legoflow.service.functional.ServicePipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AsyncPipelineDemoTest {

    @Test
    void testAsyncProcess() throws Exception {
        var sync = new ServicePipeline<String>()
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase);
        var async = new AsyncServicePipeline<>(sync);
        var result = async.process(List.of("hello", "", "world")).get(5, TimeUnit.SECONDS);
        assertThat(result).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testAsyncForEach() throws Exception {
        var sync = new ServicePipeline<Integer>().filter(i -> i > 0);
        var async = new AsyncServicePipeline<>(sync);
        var sum = new java.util.concurrent.atomic.AtomicInteger(0);
        async.forEach(List.of(-1, 2, 3, -5, 4), sum::addAndGet).get(5, TimeUnit.SECONDS);
        assertThat(sum.get()).isEqualTo(9);
    }

    @Test
    void testAsyncCollect() throws Exception {
        var sync = new ServicePipeline<String>().map(String::trim);
        var async = new AsyncServicePipeline<>(sync);
        var lengths = async.collect(List.of("  a  ", "  bb  "), String::length).get(5, TimeUnit.SECONDS);
        assertThat(lengths).containsExactly(1, 2);
    }

    @Test
    void testSyncReference() {
        var sync = new ServicePipeline<String>();
        var async = new AsyncServicePipeline<>(sync);
        assertThat(async.sync()).isSameAs(sync);
    }
}
