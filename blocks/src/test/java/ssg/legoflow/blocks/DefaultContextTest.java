package ssg.legoflow.blocks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultContextTest {

    private DefaultContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultContext();
    }

    @Test
    void testGetLoggerNotNull() {
        assertNotNull(ctx.getLogger());
    }

    @Test
    void testGetStatisticsNotNull() {
        assertNotNull(ctx.getStatistics());
    }

    @Test
    void testSetAndGetAttribute() {
        ctx.setAttribute("key", "value");
        String result = ctx.getAttribute("key");
        assertEquals("value", result);
    }

    @Test
    void testGetMissingAttributeReturnsNull() {
        String result = ctx.getAttribute("missing");
        assertNull(result);
    }

    @Test
    void testSetNullRemovesAttribute() {
        ctx.setAttribute("key", "value");
        ctx.setAttribute("key", null);
        assertNull(ctx.getAttribute("key"));
    }

    @Test
    void testSetAttributeWithType() {
        ctx.setAttribute("number", 42);
        Integer result = ctx.getAttribute("number");
        assertEquals(Integer.valueOf(42), result);
    }

    @Test
    void testHandleErrorDoesNotThrow() {
        assertDoesNotThrow(() -> ctx.handleError(new RuntimeException("test error")));
    }

    @Test
    void testMultipleAttributes() {
        ctx.setAttribute("str", "hello");
        ctx.setAttribute("num", 123);
        ctx.setAttribute("list", java.util.List.of("a", "b"));

        assertEquals("hello", ctx.getAttribute("str"));
        assertEquals(Integer.valueOf(123), ctx.getAttribute("num"));
        assertEquals(java.util.List.of("a", "b"), ctx.getAttribute("list"));
    }
}
