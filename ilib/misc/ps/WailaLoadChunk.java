package ilib.misc.ps;

import ilib.net.mock.MockingUtil;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * @author solo6975
 * @since 2022/3/31 23:42
 */
public class WailaLoadChunk extends Cheat {
	@Override
	public void onCommand(EntityPlayerSP player, String[] args) {
		PacketBuffer buf = MockingUtil.newBuffer(64);
		buf.writeByte(1).writeInt(Integer.parseInt(args[2])).writeLong(new BlockPos(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5])).toLong()).writeInt(0);
		ByteBufUtils.writeUTF8String(buf, "*");
		// write packet data
		MockingUtil.sendProxyPacket("waila", buf);
	}

	@Override
	public String toString() {
		return "利用Waila模组的bug加载服务器的任意区块, <dim> <x> <y> <z>";
	}
}
