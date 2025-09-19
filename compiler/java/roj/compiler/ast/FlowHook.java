package roj.compiler.ast;

import org.jetbrains.annotations.Nullable;
import roj.asm.insn.JumpTo;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.collect.IntList;
import roj.compiler.asm.MethodWriter;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 支持嵌套的控制流拦截器，用于实现finally-like机制（例如try和synchronized语句）。
 * 该类负责拦截方法中的return和break语句，并在这些控制流转移前插入finally代码块的执行。
 * 你还需要手动处理：[异常, 控制流继续(继续执行代码)]
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

	/**
	 * 指示是在栈上保持返回值，还是暂存到临时变量
	 */
	boolean keepReturnValueOnStack;

	/**
	 * 执行finally代码块的插入逻辑
	 *
	 * <p>该方法会：
	 * <ol>
	 *   <li>处理所有break语句，在跳转到区域外部时插入finally代码</li>
	 *   <li>处理所有return语句，在统一的目标位置插入finally代码</li>
	 *   <li>管理返回值的保存和恢复</li>
	 *   <li>处理嵌套finally拦截器的传递</li>
	 * </ol>
	 *
	 * @see MethodWriter#writeTo(MethodWriter)
	 * @param cw 方法写入器，用于生成字节码
	 * @param regionStart finally代码块区域的起始标签
	 * @param finallyEmitter finally代码块的生成器
	 * @return 插入的finally调用次数
	 */
	public int finallyExecute(MethodWriter cw, Label regionStart, Consumer<MethodWriter> finallyEmitter) {
		int calls = 0;
		Arrays.sort(breakHook.getRawArray(), 0, breakHook.size());
		for (int i = breakHook.size()-1; i >= 0; i--) {
			var segmentId = breakHook.get(i);
			Label target = ((JumpTo) cw.getSegment(segmentId)).target;
			//  当它跳转到代码块外部时，才需要执行finally
			//   外部: Label未定义（后） or 在regionStart之前
			//   同时, goto target替换成ldc + switch -> target
			if (target.isValid() && target.compareTo(regionStart) >= 0) continue;

			var fork = cw.fork();
			finallyEmitter.accept(fork);
			calls++;
			if (fork.isContinuousControlFlow())
				fork.jump(target);
			int delta = cw.replaceSegment(segmentId, fork);

			if (parent != null) parent.breakHook.add(segmentId+delta);
		}

		if (returnHook.size() > 0) {
			Type type = cw.method.returnType();
			var temp = keepReturnValueOnStack || type.type == Type.VOID ? null : cw.ctx.bp.tempVar(type);

			cw.label(returnTarget);
			if (temp != null) cw.store(temp);
			finallyEmitter.accept(cw);
			calls++;

			if (cw.isContinuousControlFlow()) {
				if (temp != null) cw.load(temp);

				if (parent == null) cw.return_(type);
				else {
					parent.returnHook.add(cw.nextSegmentId());
					cw.jump(parent.returnTarget);
				}
			}
		}

		return calls;
	}
}