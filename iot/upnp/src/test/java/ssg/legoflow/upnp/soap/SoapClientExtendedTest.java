package ssg.legoflow.upnp.soap;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpClient;
import java.time.Duration;
class SoapClientExtendedTest {

    @Test void defaultConstructorCreatesClient() {
        try (var client = new SoapClient()) {
            assertThat(client).isNotNull();
        }
    }

    @Test void constructorWithTimeout() {
        try (var client = new SoapClient(Duration.ofSeconds(10))) {
            assertThat(client).isNotNull();
        }
    }

    @Test void nullTimeoutThrows() {
        assertThatThrownBy(() -> new SoapClient(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void customHttpClientConstructor() {
        var http = HttpClient.newBuilder().build();
        try (var client = new SoapClient(http, Duration.ofSeconds(5))) {
            assertThat(client).isNotNull();
        }
    }

    @Test void nullHttpClientThrows() {
        assertThatThrownBy(() -> new SoapClient(null, Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void nullTimeoutInCustomConstructor() {
        var http = HttpClient.newBuilder().build();
        assertThatThrownBy(() -> new SoapClient(http, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void soapConstantsActionFormat() {
        String action = SoapConstants.soapAction("urn:service", "SetVolume");
        assertThat(action).isEqualTo("\"urn:service#SetVolume\"");
    }

    @Test void closeDoesNotThrow() throws Exception {
        try (var client = new SoapClient()) {
            // client is open
        }
        // closed via try-with-resources
    }
}
