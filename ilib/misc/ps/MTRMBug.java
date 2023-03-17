package ilib.misc.ps;

import ilib.net.mock.MockingUtil;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.PacketBuffer;

/**
 * @author solo6975
 * @since 2022/3/31 23:42
 */
public class MTRMBug extends Cheat {
	@Override
	public void onCommand(EntityPlayerSP player, String[] args) {
		PacketBuffer buf = MockingUtil.newBuffer(10000);
		// write packet data
		MockingUtil.sendProxyPacket("mtrm", buf);
	}

	@Override
	public String toString() {
		return "利用MTRM模组的bug执行任意指令";
	}
}
