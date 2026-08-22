package ssg.legoflow.network.modbus.server;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
/** Channel handler for Modbus server service, routing data between DataChannel and Modbus transport. */
public final class ModbusServerChannelHandler implements ChannelHandler {

    private final ModbusServerService modbusServerService;

    public ModbusServerChannelHandler(ModbusServerService modbusServerService) { 
        this.modbusServerService = modbusServerService; 
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        try { modbusServerService.consume(modbusServerService.getServiceContext(), data); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override public void onWrite(DataChannel channel) {}
    @Override public void onConnect(DataChannel channel) {}

    @Override
    public void onDisconnect(DataChannel channel) {
        try { if (modbusServerService.isConnected()) modbusServerService.disconnect(modbusServerService.getServiceContext()); }
        catch (Exception e) { onError(channel, e); }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        var ctx = modbusServerService.getServiceContext();
        if (ctx != null) ctx.setAttribute("modbus-server.error", cause);
    }

    public ModbusServerService getModbusServerService() { return modbusServerService; }
}
