package ssg.legoflow.coap.client;

/**
 * Functional interface for handling CoAP observe notifications.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface CoapObserveHandler {

    /**
     * Called when an observe notification is received.
     *
     * @param response the notification response
     * @since 0.1.0
     */
    void onNotification(CoapResponse response);
}
