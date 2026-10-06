package ssg.legoflow.messaging.amqp.sasl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Coverage for SASL mechanisms and SaslAuthenticator. */
class SaslMechanismsTest {

    @Test
    void anonymousMechanism() {
        SaslMechanism mech = new AnonymousMechanism();
        assertEquals("ANONYMOUS", mech.name());
        assertTrue(mech.initialResponse().length == 0);
        assertTrue(mech.respond(new byte[0]).length == 0);
    }

    @Test
    void plainMechanism() {
        SaslMechanism mech = new PlainMechanism("user", "pass");
        assertEquals("PLAIN", mech.name());
        String expected = "\0user\0pass";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), mech.initialResponse());
        assertTrue(mech.respond(new byte[0]).length == 0);
    }

    @Test
    void plainMechanismWithNullUsername() {
        SaslMechanism mech = new PlainMechanism(null, "pass");
        String expected = "\0\0pass";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), mech.initialResponse());
    }

    @Test
    void plainMechanismWithNullPasswordThrows() {
        assertThrows(NullPointerException.class, () -> new PlainMechanism("user", null));
    }

    @Test
    void externalMechanism() {
        SaslMechanism mech = new ExternalMechanism();
        assertEquals("EXTERNAL", mech.name());
        assertTrue(mech.initialResponse().length == 0);
    }

    @Test
    void authenticatorDefaultAllowsAnonymous() {
        SaslAuthenticator auth = new SaslAuthenticator();
        assertEquals(List.of("ANONYMOUS"), auth.mechanisms());
        assertEquals(SaslAuthenticator.Result.OK, auth.authenticate("ANONYMOUS", new byte[0]));
    }

    @Test
    void authenticatorWithCredentials() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .addCredentials("user", "pass");
        assertTrue(auth.mechanisms().contains("PLAIN"));
        assertEquals(SaslAuthenticator.Result.OK, auth.authenticate("PLAIN", "\0user\0pass".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void authenticatorRejectsBadCredentials() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .addCredentials("user", "pass");
        assertEquals(SaslAuthenticator.Result.AUTH, auth.authenticate("PLAIN", "\0user\0wrong".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void authenticatorDisablesAnonymous() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .allowAnonymous(false)
            .addCredentials("user", "pass");
        assertFalse(auth.mechanisms().contains("ANONYMOUS"));
        assertEquals(SaslAuthenticator.Result.AUTH, auth.authenticate("ANONYMOUS", new byte[0]));
    }

    @Test
    void authenticatorEnableExternal() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .enableExternal();
        assertTrue(auth.mechanisms().contains("EXTERNAL"));
        assertEquals(SaslAuthenticator.Result.OK, auth.authenticate("EXTERNAL", new byte[0]));
    }

    @Test
    void authenticatorUnknownMechanism() {
        SaslAuthenticator auth = new SaslAuthenticator();
        assertEquals(SaslAuthenticator.Result.AUTH, auth.authenticate("UNKNOWN", new byte[0]));
    }

    @Test
    void authenticatorCustomAuth() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .customAuth((mech, user, pass) -> SaslAuthenticator.Result.OK);
        // custom auth is only triggered for mechanisms not in ANONYMOUS/PLAIN/EXTERNAL
        assertEquals(SaslAuthenticator.Result.AUTH, auth.authenticate("CUSTOM", new byte[0]));
    }

    @Test
    void authenticatorPlainWithShortResponse() {
        SaslAuthenticator auth = new SaslAuthenticator()
            .addCredentials("user", "pass");
        assertEquals(SaslAuthenticator.Result.AUTH, auth.authenticate("PLAIN", new byte[2]));
    }

    @Test
    void resultEnum() {
        assertSame(SaslAuthenticator.Result.OK, SaslAuthenticator.Result.OK);
        assertSame(SaslAuthenticator.Result.AUTH, SaslAuthenticator.Result.AUTH);
        assertSame(SaslAuthenticator.Result.SYS, SaslAuthenticator.Result.SYS);
    }

    @Test
    void authFunctionInterface() {
        SaslAuthenticator.AuthFunction fn = (m, u, p) -> SaslAuthenticator.Result.SYS;
        assertEquals(SaslAuthenticator.Result.SYS, fn.authenticate("PLAIN", "user", "pass"));
    }
}
