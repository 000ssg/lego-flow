package ssg.legoflow.service.demo.functional;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LambdaServiceDemoTest {

    @Test
    void testParsingServiceConsume() {
        var parser = LambdaServiceDemo.createParsingService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        parser.connect(ctx);
        assertThat(parser.getState()).isEqualTo(ProcessorState.READY);
        parser.consume(ctx, "42", "100");
        assertThat(parser.getStatistics().getInCount(String.class)).isEqualTo(2);
        assertThat(parser.getStatistics().getOutCount(Integer.class)).isEqualTo(2);
    }

    @Test
    void testParsingServiceInvalidInput() {
        var parser = LambdaServiceDemo.createParsingService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        parser.connect(ctx);
        parser.consume(ctx, "not-a-number");
        assertThat(parser.getStatistics().getOutCount(Integer.class)).isEqualTo(1);
    }

    @Test
    void testUpperCaseService() {
        var upper = LambdaServiceDemo.createUpperCaseService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        upper.connect(ctx);
        upper.consume(ctx, "hello", "world");
        assertThat(upper.getStatistics().getInCount(String.class)).isEqualTo(2);
        assertThat(upper.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }

    @Test
    void testLambdaServiceDescriptor() {
        var parser = LambdaServiceDemo.createParsingService();
        assertThat(parser.getDescriptor().name()).isEqualTo("parser");
    }
}
