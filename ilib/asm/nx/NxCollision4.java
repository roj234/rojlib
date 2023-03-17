package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import net.minecraftforge.common.ForgeHooks;

/**
 * @author solo6975
 * @since 2022/5/2 23:45
 */
@Nixim("net.minecraft.entity.EntityLivingBase")
abstract class NxCollision4 extends EntityLivingBase {
	public NxCollision4() {
		super(null);
	}

	@Inject("/")
	public boolean isOnLadder() {
		if ((Object) this instanceof EntityPlayer && ((EntityPlayer) (Object) this).isSpectator()) {
			return false;
		} else {
			int x = MathHelper.floor(posX);
			int y = MathHelper.floor(getEntityBoundingBox().minY);
			int z = MathHelper.floor(posZ);

			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, y, z);
			boolean on = ForgeHooks.isLivingOnLadder(world.getBlockState(pos), world, pos, this);
			pos.release();
			return on;
		}
	}
}
