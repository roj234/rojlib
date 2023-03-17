package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/28 21:40
 */
@Nixim("net.minecraft.block.BlockFalling")
class NxFalling extends BlockFalling {
	@Inject("func_176503_e")
	private void checkFallable(World w, BlockPos pos) {
		if (pos.getY() < 0) return;

		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos).move(EnumFacing.DOWN);

		IBlockState state = w.getBlockState(pos1);
		if (!state.getBlock().isAir(state, w, pos1) || !canFallThrough(state)) return;

		if (!fallInstantly && w.isAreaLoaded(pos.add(-32, -32, -32), pos.add(32, 32, 32))) {
			if (!w.isRemote) {
				EntityFallingBlock ent = new EntityFallingBlock(w, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, w.getBlockState(pos));
				onStartFalling(ent);
				w.spawnEntity(ent);
			}
		} else {
			IBlockState me = w.getBlockState(pos);
			w.setBlockToAir(pos);

			do {
				pos1.setY(pos1.getY() - 1);
				state = w.getBlockState(pos1);
			} while (state.getBlock().isAir(state, w, pos1) || canFallThrough(state));

			if (pos1.getY() > 0) {
				w.setBlockState(pos1.move(EnumFacing.UP), me);
			}
		}

		pos1.release();
	}
}
