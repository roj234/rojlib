package lac.client.packets;

import ilib.ClientProxy;
import ilib.client.GuiHelper;
import lac.client.ui.GuiLogin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/**
 * @author Roj233
 * @since 2021/7/9 2:03
 */
public final class PacketLogin implements Packet<INetHandler> {
    private String payload;

    public PacketLogin() {}

    public PacketLogin(String payload) {
        this.payload = payload;
    }

    @Override
    public void readPacketData(final PacketBuffer buffer) {
        payload = buffer.readString(128);
    }

    @Override
    public void writePacketData(final PacketBuffer buffer) {
        buffer.writeString(payload);
    }

    @Override
    public void processPacket(INetHandler handler) {
        GuiHelper.openClientGui(new GuiLogin(ClientProxy.mc.currentScreen));
    }

    public static void sendToServer(String data) {
        Minecraft.getMinecraft().player.connection.sendPacket(new PacketLogin(data));
    }
}
