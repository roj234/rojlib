package roj.compiler.ast;

import org.jetbrains.annotations.Nullable;
import roj.collect.IntBiMap;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.compiler.asm.Variable;
import roj.util.TypedKey;

import java.util.Objects;

import static roj.collect.IntMap.UNDEFINED;

/**
 * Variable Initialization State.
 * 和Thaumcraft没有关系（雾
 * Is this some kind of SSA?
 * @author Roj234
 * @since 2024/6/23 0023 17:10
 */
public final class VisMap {
	private static final TypedKey<Object> DEFINED = TypedKey.of("DEFINED");

	public static final class State {
		final IntMap<Object> data;
		State(IntMap<Object> data) {this.data = data;}
	}

	private final IntBiMap<Variable> vuid = new IntBiMap<>();

	private final SimpleList<LabelNode> headHook = new SimpleList<>();
	private final IntList varCounts = new IntList();
	private final SimpleList<IntMap<Object>> varStates = new SimpleList<>();

	private IntMap<Object> varState = new IntMap<>();
	private int varCount;
	private boolean terminated;

	public void clear() {
		vuid.clear();
		varCounts.clear();
		varStates.clear();
		varState.clear();
		varCount = 0;
		terminated = false;
		headHook.clear();
	}

	// 进入代码块
	public void enter(@Nullable LabelNode immediateLabel) {
		headHook.add(immediateLabel);
		varCounts.add(varCount);

		varStates.add(varState); // 代码块开始前的状态
		varStates.add(new IntMap<>(varState)); // 代码块开始处的状态
		varState = new IntMap<>(varState); // 当前状态
	}
	// 分支
	public void orElse() {
		var prevVarCount = varCounts.get(varCounts.size()-1);

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		if (!terminated) {
			var prevVarDefined = varStates.get(varStates.size()-2);
			mergeState(prevVarCount, prevVarDefined);
		}
		terminated = false;

		var initVarDefined = varStates.getLast();
		varState = new IntMap<>(initVarDefined); // 恢复代码块开始处的状态

		for (int i = 0; i < prevVarCount; i++) {
			Object defined = varState.getOrDefault(i, UNDEFINED);
			vuid.get(i).constantValue = defined == UNDEFINED || defined == DEFINED ? null : defined;
		}
	}
	private void mergeState(int prevVarCount, IntMap<Object> prevVarDefined) {
		for (int i = 0; i < prevVarCount; i++) {
			var defined = varState.getOrDefault(i, UNDEFINED);
			if (defined == UNDEFINED) {
				prevVarDefined.putInt(i, UNDEFINED);
			} else {
				var prevDefined = prevVarDefined.getOrDefault(i, defined);
				if (prevDefined != UNDEFINED) {
					// 不相同则去除
					if (!Objects.equals(defined, prevDefined)) defined = DEFINED;
					prevVarDefined.putInt(i, defined);
				}
			}
		}
	}

	// 离开代码块
	public void exit() {
		var pop = headHook.pop();
		if (pop != null) pop.combineState(this);

		var prevVarCount = varCounts.remove(varCounts.size()-1);
		varStates.pop();
		var prevVarDefined = varStates.pop();

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		if (!terminated) mergeState(prevVarCount, prevVarDefined);

		for (int i = 0; i < prevVarCount; i++) {
			var defined = prevVarDefined.getOrDefault(i, UNDEFINED);
			var hasValue = defined != UNDEFINED;
			var _var = vuid.get(i);
			if (hasValue) {
				_var.hasValue = true;
				_var.constantValue = defined == DEFINED ? null : defined;
			} else {
				prevVarDefined.remove(i);
				_var.hasValue = false;
				_var.constantValue = null;
			}
		}

		varState = prevVarDefined;
		terminated = false;
	}

	// 控制流终止，不合并定义的变量
	public void terminate() {
		terminated = true;
	}

	// 控制流转移 to
	public State jump() {
		terminated = true;

		var prevVarCount = varCounts.get(varCounts.size()-1);
		var copyOf = new IntMap<>();
		mergeState(prevVarCount, copyOf);

		return new State(copyOf);
	}
	// 控制流转移 from
	public void orElse(State state) {
		var prevVarCount = varCounts.get(varCounts.size()-1);
		var prevVarDefined = varStates.get(varStates.size()-2);

		for (var itr = state.data.selfEntrySet().iterator(); itr.hasNext(); ) {
			if (itr.next().getIntKey() >= prevVarCount) {
				itr.remove();
			}
		}
		prevVarDefined.putAll(state.data);
	}

	// 变量
	public void add(Variable v) {
		if (v.hasValue) return;
		vuid.putByValue(varCount++, v);
	}
	public boolean hasValue(Variable v) {
		var vid = vuid.getValueOrDefault(v, -1);
		return v.hasValue || vid >= 0 && varState.getOrDefault(vid, UNDEFINED) != UNDEFINED;
	}
	public void assign(Variable var) {
		var vid = vuid.getValueOrDefault(var, -1);
		if (vid >= 0) varState.putInt(vid, var.constantValue == null ? DEFINED : var.constantValue);
		else {
			if (!var.hasValue) throw new AssertionError();
		}
	}
}