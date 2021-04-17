package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj233
 * @since 2022/4/23 0:50
 */
@Nixim("net.minecraft.entity.Entity")
abstract class NxBP1 extends Entity {
	public NxBP1() {
		super(null);
	}

	@Inject("/")
	public boolean isInsideOfMaterial(Material mat) {
		if (this.getRidingEntity() instanceof EntityBoat) {
			return false;
		} else {
			double d0 = posY + getEyeHeight();
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(posX, d0, posZ);
			IBlockState state = world.getBlockState(pos);
			Boolean result = state.getBlock().isEntityInsideMaterial(this.world, pos, state, this, d0, mat, true);
			if (result != null) {
				pos.release();
				return result;
			} else {
				try {
					return state.getMaterial() == mat && ForgeHooks.isInsideOfMaterial(mat, this, pos);
				} finally {
					pos.release();
				}
			}
		}
	}

	@Inject("/")
	protected boolean pushOutOfBlocks(double x, double y, double z) {
		if (!world.collidesWithAnyBlock(this.getEntityBoundingBox())) return false;

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, y, z);
		double dx = x - (double) pos.getX();
		double dy = y - (double) pos.getY();
		double dz = z - (double) pos.getZ();

		EnumFacing side = EnumFacing.UP;
		double minDist = 1e100;

		BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
		if (!world.isBlockFullCube(tmp.setPos(pos).move(EnumFacing.WEST)) && dx < minDist) {
			minDist = dx;
			side = EnumFacing.WEST;
		}

		if (!world.isBlockFullCube(tmp.setPos(pos).move(EnumFacing.EAST)) && 1.0D - dx < minDist) {
			minDist = 1.0D - dx;
			side = EnumFacing.EAST;
		}

		if (!world.isBlockFullCube(tmp.setPos(pos).move(EnumFacing.NORTH)) && dz < minDist) {
			minDist = dz;
			side = EnumFacing.NORTH;
		}

		if (!world.isBlockFullCube(tmp.setPos(pos).move(EnumFacing.SOUTH)) && 1.0D - dz < minDist) {
			minDist = 1.0D - dz;
			side = EnumFacing.SOUTH;
		}

		if (!world.isBlockFullCube(tmp.setPos(pos).move(EnumFacing.UP)) && 1.0D - dy < minDist) {
			minDist = 1.0D - dy;
			side = EnumFacing.UP;
		}

		tmp.release();
		pos.release();

		float f = (rand.nextFloat() * 0.2F + 0.1F) * side.getAxisDirection().getOffset();
		switch (side.getAxis().ordinal()) {
			case 0:
				motionX = f;
				motionY *= .75;
				motionZ *= .75;
				break;
			case 1:
				motionX *= .75;
				motionY = f;
				motionZ *= .75;
				break;
			case 2:
				motionX *= .75;
				motionY *= .75;
				motionZ = f;
				break;
		}

		return true;
	}

	// isOnLadder

	@Inject(value = "/", flags = Inject.FLAG_OPTIONAL)
	@SideOnly(Side.CLIENT)
	public int getBrightnessForRender() {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(posX), MathHelper.floor(posY + getEyeHeight()), MathHelper.floor(posZ));
		int l;
		if (world.isBlockLoaded(pos)) {
			l = world.getCombinedLight(pos, 0);
		} else {
			l = 0;
		}
		pos.release();
		return l;
	}

	@Inject("/")
	public float getBrightness() {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(MathHelper.floor(posX), MathHelper.floor(posY + getEyeHeight()), MathHelper.floor(posZ));
		float l;
		if (world.isBlockLoaded(pos)) {
			l = world.getLightBrightness(pos);
		} else {
			l = 0;
		}
		pos.release();
		return l;
	}
}
