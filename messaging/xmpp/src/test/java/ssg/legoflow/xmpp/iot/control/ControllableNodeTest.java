package ssg.legoflow.xmpp.iot.control;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ControllableNode}.
 *
 * @since 1.0.0
 */
class ControllableNodeTest {

    private ControllableNode node;

    @BeforeEach
    void setUp() {
        node = new ControllableNode("thermostat-1");
    }

    @Test
    void testSetParameter() {
        node.setParameter("temp", "22.5");
        assertThat(node.getParameter("temp")).isNotNull();
        assertThat(node.getParameter("temp").value()).isEqualTo("22.5");
    }

    @Test
    void testAddTypedParameter() {
        node.addParameter(ControlParameter.ofDouble("targetTemp", 22.0));
        var param = node.getParameter("targetTemp");
        assertThat(param.type()).isEqualTo(ControlParameter.ControlParameterType.DOUBLE);
    }

    @Test
    void testGetParameters() {
        node.addParameter(ControlParameter.ofDouble("temp", 22.0));
        node.addParameter(ControlParameter.ofBoolean("on", true));
        assertThat(node.getParameters()).hasSize(2);
    }

    @Test
    void testHandleControlRequest() {
        node.addParameter(ControlParameter.ofDouble("temp", 20.0));
        var request = new ControlRequest(JID.parse("user@localhost"), "thermostat-1",
                List.of(ControlParameter.ofDouble("temp", 25.0)));
        boolean success = node.handleControlRequest(request);
        assertThat(success).isTrue();
        assertThat(node.getParameter("temp").value()).isEqualTo("25.0");
    }

    @Test
    void testControlListener() {
        var requests = new ArrayList<ControlRequest>();
        node.addControlListener(requests::add);
        var request = new ControlRequest(JID.parse("user@localhost"), "thermostat-1",
                List.of(ControlParameter.ofString("mode", "heat")));
        node.handleControlRequest(request);
        assertThat(requests).hasSize(1);
    }
}
