package ssg.legoflow.xmpp.iot.control;

/**
 * Functional interface for receiving IoT control requests (XEP-0325).
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface ControlListener {

    /**
     * Called when a control request is received.
     *
     * @param request the control request
     */
    void onControlRequest(ControlRequest request);
}
