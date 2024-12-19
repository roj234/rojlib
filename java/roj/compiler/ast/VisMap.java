package roj.compiler.ast;

import roj.collect.*;
import roj.compiler.asm.Variable;

/**
 * Variable Initialization State.
 * 和Thaumcraft没有关系（雾
 * Is this some kind of SSA?
 * @author Roj234
 * @since 2024/6/23 0023 17:10
 */
public final class VisMap {
	private final IntBiMap<Variable> vuid = new IntBiMap<>();

	private final IntList varCounts = new IntList();
	private final SimpleList<MyBitSet> varStates = new SimpleList<>();
	private final SimpleList<XHashSet<Variable, ConstantState>> constantStates = new SimpleList<>();

	private MyBitSet varState = new MyBitSet();
	private int varCount;

	private boolean terminateFlag;

	private final SimpleList<LabelNode> extraHooks = new SimpleList<>();
	private LabelNode immediateHook;

	public static final class State {
		final MyBitSet data;
		State(MyBitSet data) {this.data = data;}
	}

	private static final XHashSet.Shape<Variable, ConstantState> CONSTANT_STATE_SHAPE = XHashSet.shape(Variable.class, ConstantState.class, "variable", "_next");
	private static final class ConstantState {
		Variable variable;
		Object initialValue, lastValue;
		ConstantState _next;
	}

	public void clear() {
		vuid.clear();
		varCounts.clear();
		varStates.clear();
		varState.clear();
		varCount = 0;
		terminateFlag = false;
		extraHooks.clear();
		immediateHook = null;
	}

	public void enter() {
		extraHooks.add(immediateHook);
		immediateHook = null;

		varCounts.add(varCount);

		varStates.add(varState); // prev

		var tmp = new MyBitSet();
		tmp.or(varState);
		varStates.add(tmp); // init

		tmp = new MyBitSet();
		tmp.or(varState);
		varState = tmp; // current

		constantStates.add(CONSTANT_STATE_SHAPE.create());
	}
	public void orElse() {
		var prevVarCount = varCounts.get(varCounts.size()-1);

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		if (!terminateFlag) {
			var prevVarDefined = varStates.get(varStates.size()-2);
			mergeState(prevVarCount, prevVarDefined);
		}
		terminateFlag = false;

		var initVarDefined = varStates.getLast();
		var tmp = new MyBitSet();
		tmp.or(initVarDefined);
		varState = tmp;
	}
	private void mergeState(int prevVarCount, MyBitSet prevVarDefined) {
		for (int i = 0; i < prevVarCount; i++) {
			prevVarDefined.add(varState.contains(i << 1/*DEFINED*/) ? (i << 1/*DEFINED*/) : (i << 1) + 1/*UNSET*/);
		}
		var map = constantStates.getLast();
		for (var state : map) {
			var a = state.lastValue;
			var b = state.variable.constantValue;
			state.lastValue = a == null || a.equals(b) ? b : IntMap.UNDEFINED;
			state.variable.constantValue = state.initialValue;
		}
		//map.clear();
	}

	public void exit() {
		var pop = extraHooks.pop();
		if (pop != null) pop.combineState(this);

		var prevVarCount = varCounts.remove(varCounts.size()-1);
		varStates.pop();
		var prevVarDefined = varStates.pop();

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		if (!terminateFlag) mergeState(prevVarCount, prevVarDefined);
		// TODO 由于我选择了立即序列化，在此刻进行循环中的变量状态分析是不可能的
		for (var state : constantStates.pop()) {
			state.variable.constantValue =
				state.lastValue == IntMap.UNDEFINED
				|| (state.initialValue != null && !state.initialValue.equals(state.lastValue))
					? null
					: state.lastValue;
		}

		for (int i = 0; i < prevVarCount; i++) {
			var hasValue = prevVarDefined.contains(i << 1/*DEFINED*/)
				&& !prevVarDefined.remove((i << 1) + 1/*UNSET*/);
			var _var = vuid.get(i);
			if (hasValue) {
				_var.hasValue = true;
			} else {
				prevVarDefined.remove(i<<1/*DEFINED*/);
				_var.hasValue = false;
			}
		}

		varState = prevVarDefined;
		terminateFlag = false;
	}

	// 控制流中止
	public void terminate() {
		// 控制流结束，定义的变量无法合并
		terminateFlag = true;
	}

	// 控制流转移
	void blockHook(LabelNode label) {
		if (immediateHook != null) throw new IllegalStateException("immediateHook != null");
		immediateHook = label;
	}
	public State jump() {
		terminateFlag = true;

		var prevVarCount = varCounts.get(varCounts.size()-1);
		var copyOf = new MyBitSet();
		mergeState(prevVarCount, copyOf);

		return new State(copyOf);
	}
	public void orElse(State state) {
		var prevVarCount = varCounts.get(varCounts.size()-1);
		var prevVarDefined = varStates.get(varStates.size()-2);

		state.data.removeRange(prevVarCount << 1, state.data.last()+1);
		prevVarDefined.or(state.data);
	}

	public void add(Variable v) {
		if (v.hasValue) return;
		vuid.putByValue(varCount++, v);
	}
	public boolean hasValue(Variable v) {
		var vid = vuid.getValueOrDefault(v, -1);
		return v.hasValue || vid >= 0 && varState.contains(vid << 1);
	}
	public void assign(Variable var) {
		var vid = vuid.getValueOrDefault(var, -1);
		if (vid >= 0) varState.add(vid << 1);
		else {
			if (!var.hasValue) throw new AssertionError();
		}
	}
	public void assignWithValue(Variable var) {
		var vid = vuid.getValueOrDefault(var, -1);
		if (vid >= 0 && !constantStates.isEmpty()) {
			var set = constantStates.getLast();
			if (!set.containsKey(var))
				set.computeIfAbsent(var).initialValue = var.constantValue;
		}
	}
}