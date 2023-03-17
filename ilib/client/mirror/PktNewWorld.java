package ilib.client.mirror;

import ilib.net.IMessage;
import ilib.net.IMessageHandler;
import ilib.net.MessageContext;

import net.minecraft.network.PacketBuffer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class PktNewWorld implements IMessage, IMessageHandler<PktNewWorld> {
	private int dimensionId;

	public PktNewWorld() {}

	public PktNewWorld(int i) {
		dimensionId = i;
	}

	public int getDimensionId() {
		return dimensionId;
	}

	@Override
	public void fromBytes(PacketBuffer buf) {
		dimensionId = buf.readInt();
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeInt(dimensionId);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void onMessage(PktNewWorld msg, MessageContext ctx) {
		ClientHandler.createWorld(dimensionId);
	}
}
