package ilib.asm.nx;

import ilib.misc.RedstoneUpdater;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.block.BlockRedstoneWire")
abstract class NxCachedPos3 extends BlockRedstoneWire {
	@Copy
	private static boolean isRedstoneUpdating;
	@Copy
	private static RedstoneUpdater frw;

	@Inject("func_176338_e")
	private IBlockState updateSurroundingRedstone(World world, BlockPos pos, IBlockState state) {
		if (!isRedstoneUpdating) {
			if (frw == null) frw = new RedstoneUpdater();
			isRedstoneUpdating = true;
			try {
				frw.world = world;
				frw.walk(pos);
			} finally {
				isRedstoneUpdating = false;
				frw.world = null;
			}
		}
		return state;
	}
}