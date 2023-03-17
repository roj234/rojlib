package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityPiston;

/**
 * @author Roj233
 * @since 2022/4/17 14:14
 */
@Nixim("net.minecraft.tileentity.TileEntityPiston")
class NoGhostSlime extends TileEntityPiston {
	@Shadow("field_145870_n")
	private float lastProgress;
	@Shadow("field_145873_m")
	private float progress;
	@Shadow("field_174932_a")
	private IBlockState pistonState;

	@Shadow("func_184322_i")
	private void moveCollidedEntities(float f) {}

	@Inject("func_73660_a")
	public void update() {
		lastProgress = progress;
		if (lastProgress >= 1.0F) {
			world.removeTileEntity(pos);
			invalidate();
			IBlockState state = world.getBlockState(pos);
			if (state.getBlock() == Blocks.PISTON_EXTENSION) {
				world.setBlockState(pos, pistonState, 3);
				world.neighborChanged(pos, pistonState.getBlock(), pos);
				// markBlockForUpdate
				world.notifyBlockUpdate(pos.offset(state.getValue(BlockDirectional.FACING).getOpposite()), state, state, 0);
			}
		} else {
			float f = progress + 0.5F;
			moveCollidedEntities(f);
			progress = f;
			if (progress >= 1.0F) {
				progress = 1.0F;
			}
		}
	}
}
