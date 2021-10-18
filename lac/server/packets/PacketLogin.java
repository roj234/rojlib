package lac.server.packets;

import lac.server.LoginMgr;

import net.minecraft.network.*;

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
        LoginMgr.handlePacket(((NetHandlerPlayServer) handler).player, payload);
    }

    public static void send(NetworkManager man, String reason) {
        man.sendPacket(new PacketLogin(reason));
    }
}
