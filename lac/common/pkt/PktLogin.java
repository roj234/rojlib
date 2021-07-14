package lac.common.pkt;

import ilib.ATHandler;
import ilib.ClientProxy;
import ilib.client.GuiHelper;
import lac.client.login.GuiLogin;
import lac.server.LoginMgr;
import lac.server.note.Movable;
import lac.server.note.Obfuscate;
import lac.server.note.ServerOnly;
import net.minecraft.network.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/9 2:03
 */
@Movable
public final class PktLogin implements Packet<INetHandler> {
    public static void register() {
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, PktLogin.class);
    }

    @Obfuscate
    String data;

    public PktLogin() {}

    public PktLogin(String data) {
        this.data = data;
    }

    @Override
    public void readPacketData(final PacketBuffer buffer) {
        data = buffer.readString(128);
    }

    @Override
    public void writePacketData(final PacketBuffer buffer) {
        buffer.writeString(data);
    }

    @Override
    @ServerOnly(clientReplacement = "processPacket1")
    public void processPacket(INetHandler handler) {
        LoginMgr.handlePacket(((NetHandlerPlayServer) handler).player, data);
    }

    @SideOnly(Side.CLIENT)
    public void processPacket1(INetHandler handler) {
        GuiHelper.openClientGui(new GuiLogin(ClientProxy.mc.currentScreen));
    }

    @SideOnly(Side.CLIENT)
    public static void sendToServer(String data) {
        ClientProxy.mc.player.connection.sendPacket(new PktLogin(data));
    }

    @ServerOnly
    public static void toC(NetworkManager connection) {
        connection.sendPacket(new PktLogin(""));
    }

    @ServerOnly
    public static void toC(NetworkManager connection, String reason) {
        connection.sendPacket(new PktLogin(reason));
    }
}
