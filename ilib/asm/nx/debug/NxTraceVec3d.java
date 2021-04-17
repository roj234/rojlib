package ilib.asm.nx.debug;

import ilib.ImpLib;
import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.Vec3d;

/**
 * @author Roj233
 * @since 2022/4/22 22:43
 */
@Nixim("net.minecraft.util.math.Vec3d")
class NxTraceVec3d extends Vec3d {
	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxTraceVec3d(double _lvt_1_, double _lvt_2_, double _lvt_3_) {
		super(_lvt_1_, _lvt_2_, _lvt_3_);
		if (MCHooks.traceNew == Thread.currentThread()) {
			ImpLib.logger().warn("", new Throwable());
		}
	}
}
