package roj.compiler.ast;

import org.jetbrains.annotations.Nullable;
import roj.asm.insn.JumpTo;
import roj.asm.insn.Label;
import roj.collect.IntList;
import roj.compiler.asm.MethodWriter;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 控制流劫持
 * @author Roj234
 * @since 2025/4/9 12:29
 */
class FlowHook {
	//所有return语句会带着栈上的返回值跳转到该位置
	public Label returnTarget = new Label();
	//如果你想节省代码，可以在这里修改每一个return处的JumpBlock
	public IntList returnHook = new IntList(), breakHook = new IntList();

	@Nullable FlowHook parent;

	public FlowHook() {}
	public FlowHook(FlowHook parent) {this.parent = parent;}

	public void patchWithFinally(MethodWriter cw, Label regionStart, Consumer<MethodWriter> finallyEmitter) {
		Arrays.sort(breakHook.getRawArray(), 0, breakHook.size());
		for (int i = breakHook.size()-1; i >= 0; i--) {
			var segmentId = breakHook.get(i);
			Label target = ((JumpTo) cw.getSegment(segmentId)).target;
			//  当它跳转到代码块外部时，才需要执行finally
			//   外部：Label未定义（后） or 在regionBegin之前
			//   同时,goto target替换成ldc + switch -> target
			if (target.isValid() && target.compareTo(regionStart) >= 0) continue;

			var fork = cw.fork();
			finallyEmitter.accept(fork);
			fork.jump(target);
			int delta = cw.replaceSegment(segmentId, fork);

			if (parent != null) parent.breakHook.add(segmentId+delta);
		}

		if (returnHook.size() > 0) {
			cw.label(returnTarget);
			finallyEmitter.accept(cw);
			if (parent == null) cw.return_(cw.mn.returnType());
			else cw.jump(parent.returnTarget);
		}
	}
}