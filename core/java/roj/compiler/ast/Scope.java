package roj.compiler.ast;

import roj.asm.insn.Label;
import roj.collect.ArrayList;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/10/22 15:01
 */
final class Scope {
	final Label entry;
	Label breakTarget, continueTarget;

	private List<VisMap.State> states = Collections.emptyList();

	public Scope(Label entry) {this.entry = entry;}
	public Scope(Label entry, Label breakTo, Label continueTo) {
		this.entry = entry;
		this.breakTarget = breakTo;
		this.continueTarget = continueTo;
	}

	@Override
	public String toString() {return "Scope{"+"entry="+entry+", break="+breakTarget+", continue="+continueTarget+'}';}

	void onBreak(VisMap.State state) {
		if (states.isEmpty()) states = new ArrayList<>();
		states.add(state);
	}
	void onExit(VisMap visMap) {
		for (int i = 0; i < states.size(); i++) {
			visMap.orElse(states.get(i));
		}
	}
}