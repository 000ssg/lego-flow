package ssg.legoflow.messaging.amqp.sasl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SaslTest {
    @Test void testPlainMechanismName() {
        var mech = new PlainMechanism("user", "pass");
        assertThat(mech.name()).isEqualTo("PLAIN");
    }
    @Test void testAnonymousMechanismName() {
        var mech = new AnonymousMechanism();
        assertThat(mech.name()).isEqualTo("ANONYMOUS");
    }
    @Test void testExternalMechanismName() {
        var mech = new ExternalMechanism();
        assertThat(mech.name()).isEqualTo("EXTERNAL");
    }
    @Test void testPlainMechanismAllowsNullAuthId() {
        // PLAIN allows null authId (identity used instead)
        var mech = new PlainMechanism(null, "pass");
        assertThat(mech.name()).isEqualTo("PLAIN");
        assertThat(mech.initialResponse()).isNotNull();
    }
}
