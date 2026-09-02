package ssg.legoflow.media.sip.registration;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SipRegistrationClient}.
 */
class SipRegistrationClientTest {

    @Test
    void testRegister() {
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@192.168.1.1:5060");

        var request = client.register(3600);

        assertThat(request.method()).isEqualTo(SipMethod.REGISTER);
        assertThat(request.requestUri()).isEqualTo("sip:registrar.example.com");
        assertThat(request.headers().first(SipHeaders.FROM).orElse("")).contains("sip:alice@example.com");
        assertThat(request.headers().first(SipHeaders.TO).orElse("")).contains("sip:alice@example.com");
        assertThat(request.headers().first(SipHeaders.CONTACT).orElse("")).contains("sip:alice@192.168.1.1:5060");
        assertThat(request.headers().expires()).hasValue(3600);
    }

    @Test
    void testUnregister() {
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@192.168.1.1:5060");

        var request = client.unregister();

        assertThat(request.method()).isEqualTo(SipMethod.REGISTER);
        assertThat(request.headers().expires()).hasValue(0);
    }

    @Test
    void testCSeqIncrements() {
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@192.168.1.1:5060");

        var req1 = client.register(3600);
        var req2 = client.register(3600);

        long cseq1 = req1.headers().cseq().sequence();
        long cseq2 = req2.headers().cseq().sequence();
        assertThat(cseq2).isGreaterThan(cseq1);
    }

    @Test
    void testCallIdConsistent() {
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@192.168.1.1:5060");

        var req1 = client.register(3600);
        var req2 = client.register(1800);

        assertThat(req1.headers().callId()).isEqualTo(req2.headers().callId());
        assertThat(client.callId()).isEqualTo(req1.headers().callId());
    }

    @Test
    void testRegistrationFlow() {
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@192.168.1.1:5060");
        var registrar = new SipRegistrar("example.com");

        // Register
        var regRequest = client.register(3600);
        var regResponse = registrar.handleRegister(regRequest);
        assertThat(regResponse.statusCode()).isEqualTo(200);
        assertThat(registrar.bindingCount()).isEqualTo(1);

        // Re-register
        var refreshRequest = client.register(3600);
        var refreshResponse = registrar.handleRegister(refreshRequest);
        assertThat(refreshResponse.statusCode()).isEqualTo(200);

        // Unregister
        var unregRequest = client.unregister();
        var unregResponse = registrar.handleRegister(unregRequest);
        assertThat(unregResponse.statusCode()).isEqualTo(200);
        assertThat(registrar.bindingCount()).isEqualTo(0);
    }
}
