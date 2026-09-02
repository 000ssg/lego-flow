package ssg.legoflow.mqtt.broker.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for MQTT broker service. */
public final class MqttBrokerChannelHandler implements ChannelHandler {
    private final MqttBrokerService mqttService;

    public MqttBrokerChannelHandler(MqttBrokerService mqttService) { this.mqttService = mqttService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { mqttService.consume(mqttService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (mqttService.isConnected()) mqttService.disconnect(mqttService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = mqttService.getServiceContext();
        if (ctx != null) ctx.setAttribute("mqtt.error", cause);
    }

    public MqttBrokerService getMqttService() { return mqttService; }
}
