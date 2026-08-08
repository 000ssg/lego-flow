package ssg.legoflow.network.modbus.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.nio.ByteBuffer;

/** Channel handler for Modbus client service. */
public final class ModbusClientChannelHandler implements ChannelHandler {
    private final ModbusClientService modbusService;

    public ModbusClientChannelHandler(ModbusClientService modbusService) { this.modbusService = modbusService; }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { modbusService.consume(modbusService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (modbusService.isConnected()) modbusService.disconnect(modbusService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = modbusService.getServiceContext();
        if (ctx != null) ctx.setAttribute("modbus.client.error", cause);
    }

    public ModbusClientService getModbusService() { return modbusService; }
}
