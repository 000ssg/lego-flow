package ssg.legoflow.upnp.controlpoint;

/**
 * Listener interface for device discovery events.
 *
 * <p>Notified when UPnP devices are discovered on the network or when
 * previously discovered devices go offline.
 *
 * @since 1.0.0
 */
public interface DeviceListener {

    /**
     * Called when a new device is discovered on the network.
     *
     * @param device the discovered device proxy
     * @since 1.0.0
     */
    void onDeviceAdded(DeviceProxy device);

    /**
     * Called when a previously discovered device goes offline.
     *
     * @param device the device proxy that was removed
     * @since 1.0.0
     */
    void onDeviceRemoved(DeviceProxy device);
}
