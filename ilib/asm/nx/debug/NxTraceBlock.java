package ilib.asm.nx.debug;

import ilib.ImpLib;
import ilib.misc.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.math.BlockPos;

/**
 * @author Roj233
 * @since 2022/4/22 22:43
 */
@Nixim("net.minecraft.util.math.BlockPos")
class NxTraceBlock extends BlockPos {
	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxTraceBlock(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
		super(_lvt_1_, _lvt_2_, _lvt_3_);
		if (MCHooks.traceNew == Thread.currentThread()) {
			ImpLib.logger().warn("", new Throwable());
		}
	}

	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxTraceBlock(double _lvt_1_, double _lvt_2_, double _lvt_3_) {
		super(_lvt_1_, _lvt_2_, _lvt_3_);
		if (MCHooks.traceNew == Thread.currentThread()) {
			ImpLib.logger().warn("", new Throwable());
		}
	}
}
