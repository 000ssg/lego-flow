package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CounterServiceDemoTest {

    private CounterService counter;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        counter = new CounterService();
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        counter.connect(ctx);
    }

    @Test
    void testIncrementCounter() {
        counter.consume(ctx, "increment", "increment", "increment");
        var stats = counter.getStatistics();
        assertThat(stats.getInCount(String.class)).isEqualTo(3);
        assertThat(stats.getOutCount(Integer.class)).isEqualTo(3);
    }

    @Test
    void testGetCounter() {
        counter.consume(ctx, "increment", "increment");
        counter.consume(ctx, "get");
        assertThat(counter.getStatistics().getOutCount(Integer.class)).isEqualTo(3);
    }

    @Test
    void testResetCounter() {
        counter.consume(ctx, "increment", "increment");
        counter.consume(ctx, "reset");
        counter.consume(ctx, "get");
        assertThat(counter.getStatistics().getOutCount(Integer.class)).isEqualTo(4);
    }

    @Test
    void testSessionIsolation() {
        var ctx2 = new DefaultServiceContext(ServiceUser.shared("other"));
        counter.consume(ctx, "increment", "increment");
        counter.consume(ctx2, "get");
        assertThat(counter.getStatistics().getInCount(String.class)).isEqualTo(3);
    }
}
