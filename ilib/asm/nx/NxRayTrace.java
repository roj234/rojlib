package ilib.asm.nx;

import ilib.misc.MCHooks;
import ilib.util.Reflection;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.Block;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

/**
 * @author Roj233
 * @since 2022/5/4 1:16
 */
@Nixim("/")
abstract class NxRayTrace extends Block {
	public NxRayTrace() {
		super(null);
	}

	@Nullable
	@Inject("/")
	protected RayTraceResult rayTrace(BlockPos pos, Vec3d start, Vec3d end, AxisAlignedBB box) {
		Reflection.setVec(MCHooks.d, start.x - pos.getX(), start.y - pos.getY(), start.z - pos.getZ());
		Reflection.setVec(MCHooks.e, end.x - pos.getX(), end.y - pos.getY(), end.z - pos.getZ());
		RayTraceResult r = box.calculateIntercept(MCHooks.d, MCHooks.e);
		if (r == null) return null;

		Vec3d hv = r.hitVec;
		Reflection.setVec(hv, hv.x + pos.getX(), hv.y + pos.getY(), hv.z + pos.getZ());
		return new RayTraceResult(hv, r.sideHit, pos);
	}
}
