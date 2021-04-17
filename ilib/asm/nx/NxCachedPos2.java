package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.util.Helpers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.WorldServer")
abstract class NxCachedPos2 extends WorldServer {
	public NxCachedPos2() {
		super(null, null, null, 0, null);
	}

	@Shadow
	private WorldServer.ServerBlockEventList[] blockEventQueue;
	@Shadow
	private int blockEventCacheIndex;

	@Inject("/")
	public void addBlockEvent(BlockPos pos, Block block, int id, int param) {
		BlockEventData nextTick = new BlockEventData(pos.toImmutable(), block, id, param);
		ArrayList<?> list = this.blockEventQueue[this.blockEventCacheIndex];
		if (!list.contains(nextTick)) {
			list.add(Helpers.cast(nextTick));
		}
	}
}