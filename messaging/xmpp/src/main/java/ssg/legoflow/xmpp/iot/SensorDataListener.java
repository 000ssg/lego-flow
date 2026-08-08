package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.iot.sensor.SensorData;

/**
 * Functional interface for receiving IoT sensor data updates.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface SensorDataListener {

    /**
     * Called when new sensor data is received.
     *
     * @param data the sensor data
     */
    void onSensorData(SensorData data);
}
