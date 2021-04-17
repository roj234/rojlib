package ilib.asm.nx.debug;

import ilib.ImpLib;
import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.AxisAlignedBB;

/**
 * @author Roj233
 * @since 2022/4/22 22:43
 */
@Nixim("net.minecraft.util.math.AxisAlignedBB")
class NxTraceAABB extends AxisAlignedBB {
	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxTraceAABB(double _lvt_1_, double _lvt_2_, double _lvt_3_, double _lvt_4_, double _lvt_5_, double _lvt_6_) {
		super(_lvt_1_, _lvt_2_, _lvt_3_, _lvt_4_, _lvt_5_, _lvt_6_);
		if (MCHooks.traceNew == Thread.currentThread()) {
			ImpLib.logger().warn("", new Throwable());
		}
	}
}
