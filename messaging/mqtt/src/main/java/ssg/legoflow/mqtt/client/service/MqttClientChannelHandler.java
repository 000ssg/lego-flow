package ssg.legoflow.mqtt.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for MQTT client service. */
public final class MqttClientChannelHandler implements ChannelHandler {
    private final MqttClientService mqttService;

    public MqttClientChannelHandler(MqttClientService mqttService) { this.mqttService = mqttService; }

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
        if (ctx != null) ctx.setAttribute("mqtt.client.error", cause);
    }

    public MqttClientService getMqttService() { return mqttService; }
}
