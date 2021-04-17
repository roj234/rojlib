package ilib.asm.nx;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * @author Roj233
 * @since 2022/4/27 15:59
 */
@Nixim("net.minecraft.block.BlockLeaves")
abstract class NxLeaves extends BlockLeaves {
	@Copy
	private static byte[] surroudin;

	@Inject("/")
	public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
		if (!world.isRemote && state.getValue(CHECK_DECAY) && state.getValue(DECAYABLE)) {
			int bx = pos.getX();
			int by = pos.getY();
			int bz = pos.getZ();
			if (surroudin == null) {
				surroudin = new byte[0xFFFF];
			}

			if (!world.isAreaLoaded(pos, 1)) return;

			byte[] blocks = NxLeaves.surroudin;
			if (world.isAreaLoaded(pos, 6)) {
				BlockPos.MutableBlockPos pos1 = new BlockPos.MutableBlockPos();

				for (int x = -4; x <= 4; x++) {
					for (int y = -4; y <= 4; ++y) {
						for (int z = -4; z <= 4; ++z) {
							IBlockState state1 = world.getBlockState(pos1.setPos(bx + x, by + y, bz + z));
							Block block = state1.getBlock();

							int idx = 16912 + (x << 10) + (y << 5) + z;

							if (!block.canSustainLeaves(state1, world, pos1.setPos(bx + x, by + y, bz + z))) {
								if (block.isLeaves(state1, world, pos1.setPos(bx + x, by + y, bz + z))) {
									blocks[idx] = -2;
								} else {
									blocks[idx] = -1;
								}
							} else {
								blocks[idx] = 0;
							}
						}
					}
				}

				if (blocks[16912] >= 0) {
					world.setBlockState(pos, state.withProperty(CHECK_DECAY, false), 4);
					return;
				}

				for (int pass = 1; pass <= 4; pass++) {
					for (int x = -4; x <= 4; ++x) {
						for (int y = -4; y <= 4; ++y) {
							for (int z = -4; z <= 4; ++z) {

								int idx = 16912 + (x << 10) + (y << 5) + z;

								if (blocks[idx] == pass - 1) {
									int i = idx - 1024; // x-1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}

									i = idx + 1024; // x+1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}

									i = idx - 32; // y-1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}

									i = idx + 32; // y+1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}

									i = idx - 1; // z-1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}

									i = idx + 1; // z+1
									if (blocks[i] == -2) {
										blocks[i] = (byte) pass;
									}
								}
							}
						}
					}

					if (blocks[16912] >= 0) {
						world.setBlockState(pos, state.withProperty(CHECK_DECAY, false), 4);
						return;
					}
				}
			}

			this.destroy(world, pos);
		}
	}

	@Shadow("func_176235_d")
	private void destroy(World worldIn, BlockPos pos) {}
}
