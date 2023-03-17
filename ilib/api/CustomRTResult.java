package ilib.api;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CustomRTResult extends RayTraceResult {
	public final AxisAlignedBB box;

	public CustomRTResult(Vec3d hitVec, EnumFacing sideHit, BlockPos targetPos, AxisAlignedBB box) {
		super(hitVec, sideHit, targetPos);
		this.box = box;
	}
}
