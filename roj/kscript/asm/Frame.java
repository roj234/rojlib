package roj.kscript.asm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.LineInfo;
import roj.kscript.util.VInfo;
import roj.kscript.vm.ErrorInfo;
import roj.kscript.vm.Func;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.ScriptException;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2020/9/27 12:41
 */
public class Frame extends IContext {
	public Frame(ArrayList<LineInfo> lineIndexes, Consumer<Frame> postProcessor) {
		super();
		this.lineIndexes = lineIndexes;
		lineIndexes.trimToSize();

		this.builder = postProcessor;

		// 默认栈大小 = 4
		stack = new KType[4];
	}

	Frame() {}

	Consumer<Frame> builder;

	// region 函数执行周期

	/**
	 * 运行前初始化
	 *
	 * @return can simple TCO
	 */
	public boolean init(IObject $this, ArgList args) {
		this.$this = $this;
		this.args = args;

		if (builder != null) {
			builder.accept(this);
			initChk();
			builder = null;
		}

		KScriptVM.get().pushStack(stackUsed + lvt.length);

		// 初始化参数
		int i = 0;
		int[] args1 = this.usedArgs;
		for (; i < args1.length; i++) {
			lvt[i] = args.get(args1[i]);
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

		return true;
	}

	final void initChk() {
		if (lvtDef.length > 0) {
			lvtChk = new KType[lvtDef.length];
			for (int i = 0; i < lvtDef.length; i++) {
				KType def = this.lvtDef[i];
				if (def != null) {
					this.lvtChk[i] = def.copy();
				}
			}
		}
		result = KUndefined.UNDEFINED;
	}

	/**
	 * 运行后还原状态
	 */
	public void reset() {
		for (int i = 0; i < stackUsed; i++) {
			stack[i].memory(6);
			stack[i] = null;
		}
		stackUsed = 0;

		$this = null;
		args = null;

		result = KUndefined.UNDEFINED;

		KScriptVM.get().popStack();
	}

	/**
	 * 获取返回值
	 */
	public final KType returnVal() {
		KType result = this.result;
		reset();
		return result instanceof KFunction ? ((KFunction) result).onReturn(this) : result;
	}

	/**
	 * 暂存返回值，若函数运行到此结束，则{@link Node#exec(Frame)}会返回null
	 */
	// rw
	public KType result;

	// endregion
	// region 栈

	// rw
	KType[] stack;
	// rw
	int stackUsed;

	@Nonnull
	public final KType last() {
		return stack[stackUsed - 1];
	}

	@Nonnull
	public final KType pop() {
		KType v = stack[--stackUsed].memory(6);
		stack[stackUsed] = null;
		return v;
	}

	public final void setLast(@Nonnull KType base) {
		stack[stackUsed - 1] = base;
	}

	public final void push(@Nonnull KType base) {
		if (stackUsed == stack.length) {
			KType[] plus = new KType[(int) (stackUsed * 1.5)];
			System.arraycopy(stack, 0, plus, 0, stackUsed);
			stack = plus;
		}

		stack[stackUsed] = base.memory(5);

		if (++stackUsed > 2048) throw new IllegalStateException("Stack overflow(2048): " + this);
	}

	public final void stackClear() {
		for (int i = 0; i < stackUsed; i++) {
			stack[i] = null;
		}
		stackUsed = 0;
	}

	@Nonnull
	public final KType last(int i) {
		if (i >= stackUsed) throw new ArrayIndexOutOfBoundsException(stackUsed - 1 - i);
		return stack[stackUsed - 1 - i];
	}

	public final Frame closure() {
		Frame fr = duplicate();

		IContext[] arr = fr.parents;
		for (int i = 1; i < arr.length - 1; i++) {
			arr[i] = new Closure(parents[i]);
		}

		return fr;
	}

	public final boolean working() {
		return args != null;
	}

	// endregion
	// region 异常处理.nothing

	public void pushTry(TryEnterNode node) {
		throw new UnsupportedOperationException("Unexpected call");
	}

	public TryEnterNode popTry() {
		throw new UnsupportedOperationException("Unexpected call");
	}

	public TryEnterNode popTryOrNull() {
		throw new UnsupportedOperationException("Unexpected call");
	}

	public void pushError(int stage, TryEnterNode info, ScriptException ex) {
		throw new UnsupportedOperationException("Unexpected call");
	}

	public ErrorInfo popError() {
		throw new UnsupportedOperationException("Unexpected call");
	}

	// endregion
	// region 异常处理.行号计算

	public final void trace(Node node, List<StackTraceElement> collector) {
		int line = -1;
		final ArrayList<LineInfo> linf = this.lineIndexes;
		if (owner.getSource() != null && !linf.isEmpty()) {
			Node st = owner.begin;

			int lId = 1;
			LineInfo info = linf.get(0);

			while (st != null) {
				if (st == info.node) {
					line = info.line;

					// wssb
					if (lId < linf.size()) {
						info = linf.get(lId++);
					}
				}

				if (st == node) break;

				st = st.next;
			}
		}

		collector.add(new StackTraceElement(owner.getClassName(), owner.getName(), owner.getSource(), line));
		args.trace(collector);
	}

	ArrayList<LineInfo> lineIndexes;

	// endregion
	// region 内部变量

	// owner
	Func owner;

	public final Frame init(Func owner) {
		this.owner = owner;
		return this;
	}

	public final Func owner() {
		return owner;
	}

	// this
	IObject $this;

	// arguments
	ArgList args;

	// endregion
	// region 变量, and its 作用域

	// var and let by index
	KType[] lvt, lvtDef, lvtChk;
	// used for low-level function
	String[] varNames;

	// 使用的参数 (ordered)
	int[] usedArgs;

	// 线性lets
	Map<Node, VInfo> linearDiff;

	public final void linear(Node curr) {
		applyDiff(linearDiff.get(curr));
	}

	public final void applyDiff(VInfo diff) {
		while (diff != null) {
			// ? linear
			lvt[diff.id] = diff.v;
			diff = diff.next;
		}
	}

	// 修改锁定后的上下文 (多层闭包节省时间, 还有int化的变量)
	IContext[] parents;

	@Override
	public final KType get(String key) {
		return parents[parents.length - 1].get(key);
	}

	@Override
	final KType getEx(String keys, KType def) {
		return parents[parents.length - 1].getEx(keys, def);
	}

	@Override
	public final void put(String id, KType val) {
		parents[parents.length - 1].put(id, val);
	}

	@Override
	final KType getIdx(int index) {
		return lvt[index];
	}

	@Override
	final void putIdx(int index, KType value) {
		lvt[index] = value;
	}

	// endregion

	/**
	 * 防止与{@link IObject#copy()}重名
	 */
	public Frame duplicate() {
		Frame copy = new Frame();
		copy.stack = new KType[stack.length]; // inheritance
		copy.usedArgs = usedArgs;
		copy.linearDiff = linearDiff;

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
		if ($this != null) {
			sb.append("this: ").append($this).append(", arg: ").append(args);
		}
		return sb.append('}').toString();
	}
}
