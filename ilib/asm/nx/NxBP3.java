package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/23 0:38
 */
@Nixim("/")
class NxBP3 extends ActiveRenderInfo {
	@Inject
	public static IBlockState getBlockStateAtEntityViewpoint(World worldIn, Entity entityIn, float pt) {
		Vec3d vec3d = projectViewFromEntity(entityIn, pt);
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(vec3d.x, vec3d.y, vec3d.z);
		IBlockState state = worldIn.getBlockState(pos);
		if (state.getMaterial().isLiquid()) {
			float f = 0;
			if (state.getBlock() instanceof BlockLiquid) {
				f = BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL)) - 0.11111111F;
			}

			float f1 = (float) (pos.getY() + 1) - f;
			if (vec3d.y >= (double) f1) {
				state = worldIn.getBlockState(pos.move(EnumFacing.UP));
				pos.move(EnumFacing.DOWN);
			}
		}

		state = state.getBlock().getStateAtViewpoint(state, worldIn, pos, vec3d);
		pos.release();
		return state;
	}
}
