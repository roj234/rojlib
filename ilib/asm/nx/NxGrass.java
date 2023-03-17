package ilib.asm.nx;

import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * @author Roj233
 * @since 2022/4/23 0:06
 */
@Nixim("net.minecraft.block.BlockGrass")
class NxGrass extends BlockGrass {
	@Inject("/")
	public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
		Block block = worldIn.getBlockState(MCHooksClient.get().setPos(pos).move(EnumFacing.UP)).getBlock();
		return state.withProperty(SNOWY, block == Blocks.SNOW || block == Blocks.SNOW_LAYER);
	}

	@Inject("/")
	public void updateTick(World w, BlockPos pos, IBlockState state, Random rand) {
		if (!w.isRemote) {
			if (!w.isAreaLoaded(pos, 3)) {
				return;
			}

			BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain(pos).move(EnumFacing.UP);
			if (w.getLightFromNeighbors(tmp) < 4 && w.getBlockState(tmp).getLightOpacity(w, tmp) > 2) {
				w.setBlockState(pos, Blocks.DIRT.getDefaultState());
			} else if (w.getLightFromNeighbors(tmp) >= 9) {
				for (int i = 0; i < 4; ++i) {
					tmp.setPos(pos.getX() + rand.nextInt(3) - 1, pos.getY() + rand.nextInt(5) - 3, pos.getZ() + rand.nextInt(3) - 1);

					Chunk c = w.getChunkProvider().getLoadedChunk(tmp.getX() >> 4, tmp.getZ() >> 4);
					if (c == null) {
						tmp.release();
						return;
					}

					state = c.getBlockState(tmp);
					if (state.getBlock() == Blocks.DIRT && state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.DIRT && w.getLightFromNeighbors(tmp.move(EnumFacing.UP)) >= 4) {
						state = c.getBlockState(tmp);
						if (state.getLightOpacity(w, pos.up()) <= 2) {
							w.setBlockState(tmp.move(EnumFacing.DOWN), Blocks.GRASS.getDefaultState());
						}
					}
				}
				tmp.release();
			}
		}

	}
}
