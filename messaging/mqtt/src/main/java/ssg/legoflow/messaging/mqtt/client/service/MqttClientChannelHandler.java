package ssg.legoflow.messaging.mqtt.client.service;

import ssg.legoflow.messaging.mqtt.transport.MqttPipelineTransport;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
/** Channel handler for MQTT client service. */
public final class MqttClientChannelHandler implements ChannelHandler {
    private final MqttClientService mqttService;
    private final MqttPipelineTransport transport;
    private volatile CountDownLatch connectLatch;

    public MqttClientChannelHandler(MqttClientService mqttService, MqttPipelineTransport transport) {
        this.mqttService = mqttService;
        this.transport = transport;
    }

    void setConnectLatch(CountDownLatch latch) { this.connectLatch = latch; }

    @Override
    public void onConnect(DataChannel channel) {
        if (connectLatch != null) connectLatch.countDown();
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        transport.onRead(channel, data);
    }

    @Override
    public void onWrite(DataChannel channel) {
        transport.onWrite(channel);
    }

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
