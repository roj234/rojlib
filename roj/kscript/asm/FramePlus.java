package roj.kscript.asm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.parser.ast.Expression;
import roj.kscript.type.KArray;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.LineInfo;
import roj.kscript.vm.ErrorInfo;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.ScriptException;
import roj.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2020/9/27 12:41
 */
public final class FramePlus extends Frame {
	public FramePlus(ArrayList<LineInfo> lineIndexes, Consumer<Frame> postProcessor) {
		super(lineIndexes, postProcessor);
	}

	FramePlus() {}

	Expression[] defVal;

	// region 函数执行周期

	/**
	 * 运行前初始化
	 */
	public boolean init(IObject $this, ArgList args) {
		KScriptVM.get().pushStack(stackUsed + lvt.length);

		this.$this = $this;
		this.args = args;

		if (builder != null) {
			builder.accept(this);
			initChk();
			builder = null;
		}

		// 初始化参数
		int i = 0;
		int[] args1 = this.usedArgs;
		int e = Math.min(args1.length, args.size());
		for (; i < e; i++) {
			if (args1[i] == -1) {
				lvt[--i] = _spread_args(args, i);
				break;
			}
			lvt[i] = args.get(args1[i]);
		}

		// 默认值
		if (defVal != null) {
			for (; i < args1.length; i++) {
				lvt[i] = defVal[i].compute(Collections.emptyMap());
			}
		} else {
			i = args1.length - 1;
		}

		for (int j = 0; i < lvt.length; i++, j++) {
			KType def = this.lvtDef[j];
			if (def != null) {
				KType chk = this.lvtChk[j];
				if (!def.equalsTo(chk)) chk.copyFrom(def);
				def = chk;
			} else {
				def = KUndefined.UNDEFINED;
			}
			lvt[i] = def;
		}

		return tryNodeBuf == null;
	}

	public static KType _spread_args(ArgList args, int i) {
		KArray array = new KArray(args.size() - i);
		for (int j = i; j < args.size(); j++) {
			array.add(args.get(j));
		}
		return array;
	}

	/**
	 * 运行后还原状态
	 */
	public void reset() {
		for (int i = 0; i < stackUsed; i++) {
			stack[i] = null;
		}
		stackUsed = 0;

		tryNodeUsed = 0;
		for (int i = 0; i < tryDataUsed; i++) {
			ErrorInfo info = tryDataBuf[i];
			if (info == null) break;
			info.e = null;
		}
		tryDataUsed = 0;

		$this = null;
		args = null;

		result = KUndefined.UNDEFINED;

		KScriptVM.get().popStack();
	}

	// endregion
	// region 异常处理.yesthing

	// try-catch section
	TryEnterNode[] tryNodeBuf;
	// try-catch exception temporary holder
	ErrorInfo[] tryDataBuf;
	int tryNodeUsed, tryDataUsed;

	public void pushTry(TryEnterNode node) {
		tryNodeBuf[tryNodeUsed++] = node;
	}

	public TryEnterNode popTry() {
		return tryNodeBuf[--tryNodeUsed];
	}

	public TryEnterNode popTryOrNull() {
		return tryNodeUsed == 0 ? null : tryNodeBuf[--tryNodeUsed];
	}

	public void pushError(int stage, TryEnterNode info, ScriptException ex) {
		ErrorInfo ei = tryDataBuf[tryDataUsed++];
		if (ei == null) {tryDataBuf[tryDataUsed - 1] = new ErrorInfo(stage, info, ex);} else {
			ei.stage = (byte) stage;
			ei.info = info;
			ei.e = ex;
		}
	}

	public ErrorInfo popError() {
		return tryDataUsed == 0 ? ErrorInfo.NONE : tryDataBuf[--tryDataUsed];
	}

	// endregion

	@Override
	public FramePlus duplicate() {
		FramePlus copy = new FramePlus();
		copy.stack = new KType[stack.length]; // inheritance
		copy.usedArgs = usedArgs;
		copy.linearDiff = linearDiff;

		int l = tryNodeBuf.length;
		copy.tryNodeBuf = new TryEnterNode[l];
		copy.tryDataBuf = new ErrorInfo[l];

		if (lvt.length == 0) {
			copy.lvt = copy.lvtChk = copy.lvtDef = lvt;
			copy.parents = parents;
		} else {
			copy.lvtDef = lvtDef;
			copy.lvt = new KType[lvt.length];
			copy.initChk();
			copy.parents = parents.clone();
			copy.parents[0] = copy;
		}
		copy.owner = owner;
		copy.lineIndexes = lineIndexes;
		return copy;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("KFrame{");
		if (result != null) {
			sb.append("<return>: ").append(result).append(", ");
		}
		if (stackUsed > 0) {
			sb.append("<stack>: [").append(ArrayUtil.toString(stack, 0, stackUsed)).append("], ");
		}
		sb.append("<var>: [").append(ArrayUtil.toString(lvt, 0, lvt.length)).append("], ");
		if (tryDataUsed > 0) sb.append("<try>: ").append(ArrayUtil.toString(tryDataBuf, 0, tryDataUsed)).append("], ");
		if ($this != null) {
			sb.append("this: ").append($this).append(", arg: ").append(args);
		}
		return sb.append('}').toString();
	}
}
