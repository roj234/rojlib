package ilib.net.packet;

import ilib.ClientProxy;
import ilib.net.AddMinecraftPacket;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2020/10/1 17:53
 */
@AddMinecraftPacket(AddMinecraftPacket.TO_CLIENT)
public class SPacketSetPlayerId implements Packet<INetHandlerPlayClient> {
	private int id;

	public SPacketSetPlayerId() {}

	public SPacketSetPlayerId(int id) {
		this.id = id;
	}

	@Override
	public void readPacketData(final PacketBuffer buffer) {
		this.id = buffer.readVarInt();
	}

	@Override
	public void writePacketData(final PacketBuffer buffer) {
		buffer.writeVarInt(id);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void processPacket(INetHandlerPlayClient handler) {
		ClientProxy.mc.player.setEntityId(id);
	}
}
