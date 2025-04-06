package roj.compiler.ast;

import org.jetbrains.annotations.Nullable;
import roj.asm.insn.JumpBlock;
import roj.asm.insn.Label;
import roj.collect.IntList;
import roj.compiler.asm.MethodWriter;

import java.util.Arrays;
import java.util.function.Consumer;

import static roj.asm.Opcodes.IRETURN;

/**
 * 控制流劫持
 * @author Roj234
 * @since 2025/4/9 12:29
 */
public class FlowHookNode {
	public Label returnTarget = new Label();
	public IntList returnHook = new IntList(), breakHook = new IntList();

	@Nullable FlowHookNode prev;

	public FlowHookNode() {}
	public FlowHookNode(FlowHookNode flowHook) {prev = flowHook;}

	public void addFinally(MethodWriter cw, Label regionBegin, Consumer<MethodWriter> writer) {
		Arrays.sort(breakHook.getRawArray(), 0, breakHook.size());
		for (int i = breakHook.size()-1; i >= 0; i--) {
			var segmentId = breakHook.get(i);
			Label target = ((JumpBlock) cw.getSegment(segmentId)).target;
			//  当它跳转到代码块外部时，才需要执行finally
			//   外部：Label未定义（后） or 在regionBegin之前
			//   同时,goto target替换成ldc + switch -> target
			if (target.isValid() && target.compareTo(regionBegin) >= 0) continue;

			var fork = cw.fork();
			writer.accept(fork);
			fork.jump(target);
			int delta = cw.replaceSegment(segmentId, fork);

			if (prev != null) prev.breakHook.add(segmentId+delta);
		}

		if (returnHook.size() > 0) {
			cw.label(returnTarget);
			writer.accept(cw);
			if (prev == null) cw.one(cw.mn.returnType().shiftedOpcode(IRETURN));
			else cw.jump(prev.returnTarget);
		}
	}
}