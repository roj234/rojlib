package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeModContainer;

/**
 * @author Roj233
 * @since 2022/5/16 5:13
 */
@Nixim("/")
class NxCollision3 extends ForgeHooks {
	@Inject("/")
	public static boolean isLivingOnLadder(IBlockState state, World world, BlockPos pos, EntityLivingBase entity) {
		boolean isSpectator = entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator();
		if (isSpectator) {
			return false;
		} else if (!ForgeModContainer.fullBoundingBoxLadders) {
			return state.getBlock().isLadder(state, world, pos, entity);
		} else {
			BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
			AxisAlignedBB bb = entity.getEntityBoundingBox();
			int mX = MathHelper.floor(bb.minX);
			int mY = MathHelper.floor(bb.minY);
			int mZ = MathHelper.floor(bb.minZ);

			for (int y2 = mY; (double) y2 < bb.maxY; ++y2) {
				for (int x2 = mX; (double) x2 < bb.maxX; ++x2) {
					for (int z2 = mZ; (double) z2 < bb.maxZ; ++z2) {
						state = world.getBlockState(tmp.setPos(x2, y2, z2));
						if (state.getBlock().isLadder(state, world, tmp, entity)) {
							tmp.release();
							return true;
						}
					}
				}
			}

			tmp.release();
			return false;
		}
	}
}
