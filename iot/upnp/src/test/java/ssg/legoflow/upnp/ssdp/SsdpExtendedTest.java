package ssg.legoflow.upnp.ssdp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
class SsdpExtendedTest {

    private static final SocketAddress LOCAL_ADDR = InetSocketAddress.createUnresolved("127.0.0.1", 9000);

    @Test void ssdpConstantsValues() {
        assertThat(SsdpConstants.MULTICAST_ADDRESS).isNotBlank();
        assertThat(SsdpConstants.MULTICAST_PORT).isEqualTo(1900);
    }

    @Test void ssdpMessageTypes() {
        for (var type : SsdpMessageType.values()) {
            assertThat(type.name()).isNotBlank();
        }
    }

    @Test void aliveMessage() {
        var msg = SsdpMessage.alive(
                "http://10.0.0.5:8080/desc.xml",
                "urn:schemas-upnp-org:device:MediaServer:1",
                "uuid:alive-test::urn:schemas-upnp-org:device:MediaServer:1",
                "TestServer/1.0", 1800);
        assertThat(msg.location()).isPresent();
    }

    @Test void byebyeMessage() {
        var msg = SsdpMessage.byebye(
                "urn:schemas-upnp-org:device:MediaServer:1",
                "uuid:byebyetest::urn:schemas-upnp-org:device:MediaServer:1");
        assertThat(msg.type()).isEqualTo(SsdpMessageType.NOTIFY_BYEBYE);
    }

    @Test void searchMessage() {
        var msg = SsdpMessage.search("ssdp:all", 3);
        assertThat(msg.type()).isEqualTo(SsdpMessageType.M_SEARCH);
    }

    @Test void messageMaxAge() {
        var msg = SsdpMessage.alive(
                "http://10.0.0.5/desc.xml",
                "urn:schemas-upnp-org:device:MediaServer:1",
                "uuid:maxage::urn:schemas-upnp-org:device:MediaServer:1",
                "Test/1.0", 3600);
        assertThat(msg.maxAge()).isEqualTo(3600);
    }

    @Test void ssdpListenerCanBeInstantiated() {
        SsdpListener listener = (msg) -> {};
        assertThat(listener).isNotNull();
    }
}
