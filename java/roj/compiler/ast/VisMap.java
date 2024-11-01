package roj.compiler.ast;

import roj.collect.IntBiMap;
import roj.collect.IntList;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.Variable;
import roj.compiler.context.GlobalContext;

/**
 * 和Thaumcraft没有关系（雾
 * @author Roj234
 * @since 2024/6/23 0023 17:10
 */
public class VisMap {
	private final IntBiMap<Variable> vuid = new IntBiMap<>();

	private final IntList varCounts = new IntList();
	private final SimpleList<MyBitSet> varStates = new SimpleList<>();

	private MyBitSet varState = new MyBitSet();
	private int varCount;
	private boolean terminateFlag;

	static class State {

	}

	public void clear() {
		vuid.clear();
		varCounts.clear();
		varStates.clear();
		varState.clear();
		varCount = 0;
		terminateFlag = false;
	}

	public void enter() {
		varCounts.add(varCount);

		varStates.add(varState); // prev

		var tmp = new MyBitSet();
		tmp.or(varState);
		varStates.add(tmp); // init

		tmp = new MyBitSet();
		tmp.or(varState);
		varState = tmp; // current
		GlobalContext.debugLogger().info("进入分支语句，当前变量数:"+varCount+"/"+vuid.size());
	}
	public void orElse() {
		var prevVarCount = varCounts.get(varCounts.size()-1);

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		if (!terminateFlag) {
			var prevVarDefined = varStates.get(varStates.size()-2);
			for (int i = 0; i < prevVarCount; i++) {
				prevVarDefined.add(varState.contains(i << 1/*DEFINED*/) ? (i << 1/*DEFINED*/) : (i << 1) + 1/*UNSET*/);
			}
		}
		terminateFlag = false;

		var initVarDefined = varStates.getLast();
		var tmp = new MyBitSet();
		tmp.or(initVarDefined);
		varState = tmp;
	}
	public void terminate() {
		// 控制流结束，定义的变量无法合并
		terminateFlag = true;
	}
	public State jump() {
		terminateFlag = true;
		// TODO not finished
		return null;
	}
	public void orElse(State state) {

	}
	public void exit() {
		var prevVarCount = varCounts.remove(varCounts.size()-1);
		var prevVarDefined = varStates.get(varStates.size()-2);
		varStates.pop();
		varState = varStates.pop();

		for (int i = prevVarCount; i < varCount; i++)
			vuid.remove(i);
		varCount = prevVarCount;

		for (int i = 0; i < prevVarCount; i++) {
			boolean hasValue = varState.contains(i << 1/*DEFINED*/) && !varState.remove((i << 1) + 1/*UNSET*/) && (terminateFlag || prevVarDefined.contains(i << 1/*DEFINED*/));
			if (hasValue) {
				GlobalContext.debugLogger().info("退出分支语句，变量{}已在所有分支有值", vuid.get(i).name);
				vuid.remove(i).hasValue = true;
			} else {
				GlobalContext.debugLogger().info("退出分支语句，变量{}未在所有分支有值", vuid.get(i).name);
				varState.remove(i<<1/*DEFINED*/);
			}
		}
	}

	public boolean hasValue(Variable v) {
		var vid = vuid.getValueOrDefault(v, -1);
		return v.hasValue || vid >= 0 && varState.contains(vid << 1);
	}

	public void add(Variable v) {
		if (v.hasValue) return;
		vuid.putByValue(varCount++, v);
	}

	public void assign(Variable var) {
		var vid = vuid.getValueOrDefault(var, -1);
		if (vid >= 0) varState.add(vid << 1);
		else {
			if (!var.hasValue) throw new AssertionError();
		}
	}
}