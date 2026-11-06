package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GlobalRequest}.
 */
class GlobalRequestTest {

    @Test
    void tcpIpForwardConstants() {
        assertEquals("tcpip-forward", GlobalRequest.TCPIP_FORWARD);
        assertEquals("cancel-tcpip-forward", GlobalRequest.CANCEL_TCPIP_FORWARD);
    }

    @Test
    void encodeTcpIpForward() {
        byte[] request = GlobalRequest.encodeTcpIpForward("0.0.0.0", 8080);

        assertNotNull(request);
        assertTrue(request.length > 0);
        // First byte should be SSH_MSG_GLOBAL_REQUEST (80)
        assertEquals(80, request[0]);
    }

    @Test
    void encodeCancelTcpIpForward() {
        byte[] request = GlobalRequest.encodeCancelTcpIpForward("0.0.0.0", 8080);

        assertNotNull(request);
        assertTrue(request.length > 0);
        // First byte should be SSH_MSG_GLOBAL_REQUEST (80)
        assertEquals(80, request[0]);
    }

    @Test
    void encodeTcpIpForwardWithDifferentAddress() {
        byte[] request = GlobalRequest.encodeTcpIpForward("127.0.0.1", 22);

        assertNotNull(request);
        assertTrue(request.length > 0);
    }

    @Test
    void encodeCancelTcpIpForwardWithDifferentAddress() {
        byte[] request = GlobalRequest.encodeCancelTcpIpForward("192.168.1.1", 443);

        assertNotNull(request);
        assertTrue(request.length > 0);
    }
}
