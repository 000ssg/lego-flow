package ssg.legoflow.service.demo.combined;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.blocks.StateListener;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ServiceLifecycleDemoTest {

    @Test
    void testFullLifecycle() {
        var echo = new EchoService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        var transitions = new ArrayList<String>();

        echo.addStateListener((from, to) -> transitions.add(from + " -> " + to));

        assertThat(echo.getState()).isEqualTo(ProcessorState.IDLE);

        echo.connect(ctx);
        assertThat(echo.getState()).isEqualTo(ProcessorState.READY);
        assertThat(echo.isConnected()).isTrue();

        echo.consume(ctx, "test-data");
        assertThat(echo.getStatistics().getInCount(String.class)).isEqualTo(1);

        echo.disconnect(ctx);
        assertThat(echo.isConnected()).isFalse();

        echo.close();
        assertThat(echo.getState()).isEqualTo(ProcessorState.STOPPED);

        assertThat(transitions).containsExactly(
                "IDLE -> CONNECTING",
                "CONNECTING -> READY",
                "READY -> STOPPED"
        );
    }

    @Test
    void testConnectDisconnectReconnect() {
        var echo = new EchoService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());

        echo.connect(ctx);
        assertThat(echo.isConnected()).isTrue();

        echo.disconnect(ctx);
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testMultipleConsumeOperations() {
        var echo = new EchoService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        echo.connect(ctx);

        for (int i = 0; i < 100; i++) {
            echo.consume(ctx, "msg-" + i);
        }

        assertThat(echo.getStatistics().getInCount(String.class)).isEqualTo(100);
        assertThat(echo.getStatistics().getOutCount(String.class)).isEqualTo(100);
    }

    @Test
    void testStateListenerRemoval() {
        var echo = new EchoService();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        var count = new java.util.concurrent.atomic.AtomicInteger(0);

        StateListener listener = (from, to) -> count.incrementAndGet();
        echo.addStateListener(listener);
        echo.connect(ctx);
        echo.removeStateListener(listener);
        echo.close();

        assertThat(count.get()).isEqualTo(2);
    }
}
