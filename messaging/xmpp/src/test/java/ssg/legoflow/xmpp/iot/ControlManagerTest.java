package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.control.ControllableNode;
import ssg.legoflow.xmpp.iot.control.ControlParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ControlManager}.
 *
 * @since 1.0.0
 */
class ControlManagerTest {

    private ControlManager manager;

    @BeforeEach
    void setUp() {
        manager = new ControlManager();
    }

    @Test
    void testRegisterControllable() {
        var node = new ControllableNode("therm-1");
        manager.registerControllable(node);
        assertThat(manager.getControllable("therm-1")).isNotNull();
    }

    @Test
    void testSendControlLocal() {
        var node = new ControllableNode("therm-1");
        node.addParameter(ControlParameter.ofDouble("temp", 20.0));
        manager.registerControllable(node);

        boolean success = manager.sendControl(
                JID.parse("therm-1@localhost"), "temp", "25.0").join();
        assertThat(success).isTrue();
        assertThat(node.getParameter("temp").value()).isEqualTo("25.0");
    }

    @Test
    void testSendControlMultipleParams() {
        var node = new ControllableNode("therm-1");
        node.addParameter(ControlParameter.ofDouble("temp", 20.0));
        node.addParameter(ControlParameter.ofBoolean("on", false));
        manager.registerControllable(node);

        var params = List.of(
                ControlParameter.ofDouble("temp", 23.0),
                ControlParameter.ofBoolean("on", true));
        boolean success = manager.sendControl(JID.parse("therm-1@localhost"), params).join();
        assertThat(success).isTrue();
    }

    @Test
    void testSendControlRemote() {
        boolean success = manager.sendControl(
                JID.parse("remote@other.com"), "param", "value").join();
        assertThat(success).isTrue();
    }

    @Test
    void testGetControllables() {
        manager.registerControllable(new ControllableNode("n1"));
        manager.registerControllable(new ControllableNode("n2"));
        assertThat(manager.getControllables()).hasSize(2);
    }
}
