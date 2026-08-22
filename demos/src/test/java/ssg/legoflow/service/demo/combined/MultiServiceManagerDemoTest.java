package ssg.legoflow.service.demo.combined;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.*;
import ssg.legoflow.service.demo.procedural.EchoService;
import ssg.legoflow.service.functional.ServiceBuilder;
import ssg.legoflow.service.manager.AbstractServicesManager;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class MultiServiceManagerDemoTest {

    private AbstractServicesManager manager;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new AbstractServicesManager(ctx);
    }

    @Test
    void testMultipleServicesStartAll() {
        var echo = new EchoService();
        var upper = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("upper", "Upper", 1))
                .onConvertToOutput((c, i) -> {
                    var r = new String[i.length];
                    for (int x = 0; x < i.length; x++) r[x] = i[x].toUpperCase();
                    return r;
                })
                .onConvertToInput((c, o) -> o)
                .build();

        manager.register(echo);
        manager.register(upper);
        manager.startAll();

        assertThat(echo.isConnected()).isTrue();
        assertThat(upper.isConnected()).isTrue();
        var states = manager.getStates();
        assertThat(states).containsEntry("echo", ProcessorState.READY);
        assertThat(states).containsEntry("upper", ProcessorState.READY);
    }

    @Test
    void testStopAllReverseOrder() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        manager.stopAll();
        assertThat(echo.isConnected()).isFalse();
    }

    @Test
    void testStartWithDependencies() {
        var dep = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("dependency", "Dep", 0, List.of()))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();
        var dependent = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("dependent", "Dependent", 1, List.of("dependency")))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();

        manager.register(dep);
        manager.register(dependent);
        manager.start("dependent");

        assertThat(dep.isConnected()).isTrue();
        assertThat(dependent.isConnected()).isTrue();
    }

    @Test
    void testStopCascadesToDependents() {
        var dep = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("base", "Base", 0, List.of()))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();
        var dependent = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("child", "Child", 1, List.of("base")))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();

        manager.register(dep);
        manager.register(dependent);
        manager.startAll();

        manager.stop("base");
        assertThat(dep.isConnected()).isFalse();
        assertThat(dependent.isConnected()).isFalse();
    }

    @Test
    void testManagerClose() {
        var echo = new EchoService();
        manager.register(echo);
        manager.startAll();
        manager.close();
        assertThat(manager.getServices()).isEmpty();
    }
}
