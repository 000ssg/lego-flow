package ssg.legoflow.service.functional;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ServicePipelineTest {

    @Test
    void testMapTransforms() {
        var pipeline = new ServicePipeline<String>().map(String::toUpperCase);
        assertThat(pipeline.process(List.of("hello", "world"))).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testFilterRemoves() {
        var pipeline = new ServicePipeline<Integer>().filter(i -> i > 3);
        assertThat(pipeline.process(List.of(1, 2, 3, 4, 5))).containsExactly(4, 5);
    }

    @Test
    void testChainingFilterThenMap() {
        var pipeline = new ServicePipeline<String>()
                .filter(s -> !s.isEmpty())
                .map(String::trim)
                .map(String::toUpperCase);
        assertThat(pipeline.process(List.of("  hello  ", "", " world ")))
                .containsExactly("HELLO", "WORLD");
    }

    @Test
    void testForEach() {
        var pipeline = new ServicePipeline<String>().map(String::toUpperCase);
        var results = new ArrayList<String>();
        pipeline.forEach(List.of("a", "b"), results::add);
        assertThat(results).containsExactly("A", "B");
    }

    @Test
    void testCollect() {
        var pipeline = new ServicePipeline<String>().filter(s -> s.length() > 2);
        var lengths = pipeline.collect(List.of("hi", "hello", "yo", "world"), String::length);
        assertThat(lengths).containsExactly(5, 5);
    }

    @Test
    void testEmptyInput() {
        var pipeline = new ServicePipeline<String>().map(String::toUpperCase);
        assertThat(pipeline.process(List.of())).isEmpty();
    }
}
