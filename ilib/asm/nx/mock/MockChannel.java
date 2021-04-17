package ilib.asm.nx.mock;

import ilib.net.mock.MockingUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.network.INetHandler;
import net.minecraft.network.play.server.SPacketCustomPayload;

import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

/**
 * @author solo6975
 * @since 2022/3/31 21:41
 */
@Nixim("/")
class MockChannel extends FMLProxyPacket {
	public MockChannel(SPacketCustomPayload original) {
		super(original);
	}

	@Inject("/")
	public void processPacket(INetHandler h) {
		if (!MockingUtil.interceptPacket(this, h)) {
			super.processPacket(h);
		}
	}
}
