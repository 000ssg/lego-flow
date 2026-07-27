package ssg.legoflow.service.functional;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class ServiceBuilderTest {

    @Test
    void testBuildSimpleService() {
        var service = ServiceBuilder.of(String.class, String.class)
                .descriptor("test", "Test service")
                .onConvertToOutput((ctx, input) -> input)
                .onConvertToInput((ctx, output) -> output)
                .build();

        assertThat(service.getDescriptor().name()).isEqualTo("test");
        assertThat(service.getState()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testBuildWithConversion() {
        var service = ServiceBuilder.of(String.class, Integer.class)
                .descriptor("parser", "Parses strings")
                .onConvertToOutput((ctx, input) -> {
                    var result = new Integer[input.length];
                    for (int i = 0; i < input.length; i++) result[i] = Integer.parseInt(input[i]);
                    return result;
                })
                .onConvertToInput((ctx, output) -> {
                    var result = new String[output.length];
                    for (int i = 0; i < output.length; i++) result[i] = output[i].toString();
                    return result;
                })
                .build();

        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        service.connect(ctx);
        service.consume(ctx, "42");
        assertThat(service.getStatistics().getInCount(String.class)).isEqualTo(1);
    }

    @Test
    void testBuildWithConnectHandler() {
        var connected = new AtomicBoolean(false);
        var service = ServiceBuilder.of(String.class, String.class)
                .descriptor("test", "Test")
                .onConvertToOutput((ctx, input) -> input)
                .onConvertToInput((ctx, output) -> output)
                .onConnect((ctx, svc) -> connected.set(true))
                .build();

        service.connect(new DefaultServiceContext(ServiceUser.anonymous()));
        assertThat(connected.get()).isTrue();
    }

    @Test
    void testBuildWithDescriptorObject() {
        var desc = new ServiceDescriptor("svc", "Service", 5);
        var service = ServiceBuilder.of(String.class, String.class)
                .descriptor(desc)
                .onConvertToOutput((ctx, input) -> input)
                .onConvertToInput((ctx, output) -> output)
                .build();

        assertThat(service.getPriority()).isEqualTo(5);
    }

    @Test
    void testBuildMissingDescriptorThrows() {
        assertThatThrownBy(() ->
                ServiceBuilder.of(String.class, String.class)
                        .onConvertToOutput((ctx, input) -> input)
                        .onConvertToInput((ctx, output) -> output)
                        .build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("ServiceDescriptor");
    }

    @Test
    void testBuildMissingConvertToOutputThrows() {
        assertThatThrownBy(() ->
                ServiceBuilder.of(String.class, String.class)
                        .descriptor("test", "Test")
                        .onConvertToInput((ctx, output) -> output)
                        .build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("convertToOutput");
    }
}
