package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
class EtcdClientIntegrationTest {

    private EtcdConfig config;
    private EtcdClient client;

    @BeforeEach
    void setUp() {
        config = EtcdConfig.builder()
                .endpoints(List.of(
                        new InetSocketAddress("localhost", 2379),
                        new InetSocketAddress("localhost", 2380)))
                .build();
        client = new EtcdClient(config);
    }

    @Test
    void connect_toEndpoints() {
        client.connect().join();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void connect_selectsFirstEndpoint() {
        client.connect().join();
        assertThat(client.currentEndpoint()).isEqualTo(
                new InetSocketAddress("localhost", 2379));
    }

    @Test
    void endpointDescription() {
        String desc = client.endpointDescription();
        assertThat(desc).contains("localhost");
        assertThat(desc).contains("2379");
    }

    @Test
    void config_returnsConfig() {
        assertThat(client.config()).isEqualTo(config);
    }

    @Test
    void onStateChange_listener_notified() throws Exception {
        List<String> states = new CopyOnWriteArrayList<>();
        client.onStateChange(states::add);

        client.connect().join();
        assertThat(states).contains("connected");

        client.close();
        assertThat(states).contains("disconnected");
    }

    @Test
    void multipleListeners() throws Exception {
        List<String> states1 = new CopyOnWriteArrayList<>();
        List<String> states2 = new CopyOnWriteArrayList<>();

        client.onStateChange(states1::add);
        client.onStateChange(states2::add);

        client.connect().join();
        assertThat(states1).contains("connected");
        assertThat(states2).contains("connected");

        client.close();
        assertThat(states1).contains("disconnected");
        assertThat(states2).contains("disconnected");
    }

    @Test
    void nullListener_throws() {
        assertThatThrownBy(() -> client.onStateChange(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void close_disconnects() {
        client.connect().join();
        client.close();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void doubleConnect() {
        client.connect().join();
        assertThatCode(() -> client.connect().join()).doesNotThrowAnyException();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void nullConfig_throws() {
        assertThatThrownBy(() -> new EtcdClient(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultEndpoint() {
        EtcdConfig defaultConfig = EtcdConfig.builder().build();
        EtcdClient defaultClient = new EtcdClient(defaultConfig);
        assertThat(defaultClient.config().endpoints()).hasSize(1);
    }
}
