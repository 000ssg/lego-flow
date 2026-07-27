package ssg.legoflow.blocks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextTest {

    @Test
    void testDefaultContextHasLogger() {
        var ctx = new DefaultContext();
        assertThat(ctx.getLogger()).isNotNull();
    }

    @Test
    void testDefaultContextHasStatistics() {
        var ctx = new DefaultContext();
        assertThat(ctx.getStatistics()).isNotNull();
    }

    @Test
    void testSetAndGetAttribute() {
        var ctx = new DefaultContext();

        ctx.setAttribute("key", "value");
        String result = ctx.getAttribute("key");

        assertThat(result).isEqualTo("value");
    }

    @Test
    void testGetMissingAttributeReturnsNull() {
        var ctx = new DefaultContext();

        String result = ctx.getAttribute("missing");

        assertThat(result).isNull();
    }

    @Test
    void testSetNullRemovesAttribute() {
        var ctx = new DefaultContext();
        ctx.setAttribute("key", "value");

        ctx.setAttribute("key", null);
        String result = ctx.getAttribute("key");

        assertThat(result).isNull();
    }

    @Test
    void testHandleErrorDoesNotThrow() {
        var ctx = new DefaultContext();
        ctx.handleError(new RuntimeException("test error"));
    }
}
