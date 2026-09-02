package ssg.legoflow.service.demo.functional;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class PipelineDemoTest {

    @Test
    void testPipelineFiltersAndTransforms() {
        var demo = new PipelineDemo();
        var input = new ArrayList<String>();
        input.add("  hello  ");
        input.add("");
        input.add("  world  ");
        input.add(null);
        input.add("   ");
        var result = demo.process(input);
        assertThat(result).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testPipelineEmptyInput() {
        var demo = new PipelineDemo();
        assertThat(demo.process(List.of())).isEmpty();
    }

    @Test
    void testPipelineAllFiltered() {
        var demo = new PipelineDemo();
        var input = new ArrayList<String>();
        input.add("");
        input.add("   ");
        input.add(null);
        assertThat(demo.process(input)).isEmpty();
    }

    @Test
    void testPipelineSingleItem() {
        var demo = new PipelineDemo();
        assertThat(demo.process(List.of("  test  "))).containsExactly("TEST");
    }
}
