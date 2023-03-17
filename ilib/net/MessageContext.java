package ilib.net;

import ilib.ClientProxy;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MessageContext {
	public final Side side;
	public final INetHandler handler;

	public MessageContext(INetHandler handler, Side side) {
		this.side = side;
		this.handler = handler;
	}

	public NetHandlerPlayServer getServerHandler() {
		return ((NetHandlerPlayServer) handler);
	}

	public NetHandlerPlayClient getClientHandler() {
		return ((NetHandlerPlayClient) handler);
	}

	public EntityPlayer getPlayer() {
		return side == Side.SERVER ? getServerHandler().player : getClientPlayer();
	}

	@SideOnly(Side.CLIENT)
	private static EntityPlayer getClientPlayer() {
		return ClientProxy.mc.player;
	}
}