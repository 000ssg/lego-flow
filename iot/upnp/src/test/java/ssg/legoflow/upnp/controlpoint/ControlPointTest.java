package ssg.legoflow.upnp.controlpoint;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ControlPointTest {
    @Test void testControlPointCreation() throws Exception {
        try (var cp = new ControlPoint()) {
            assertThat(cp).isNotNull();
        }
    }
}
