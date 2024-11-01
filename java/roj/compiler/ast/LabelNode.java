package roj.compiler.ast;

import roj.asm.visitor.Label;
import roj.collect.SimpleList;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/10/22 15:01
 */
final class LabelNode {
	final Label head;
	Label onBreak, onContinue;

	private List<VisMap.State> states = Collections.emptyList();

	public LabelNode(Label head) {this.head = head;}
	public LabelNode(Label head, Label breakTo, Label continueTo) {
		this.head = head;
		this.onBreak = breakTo;
		this.onContinue = continueTo;
	}

	@Override
	public String toString() {return "LabelGroup{"+"head="+head+", break="+onBreak+", continue="+onContinue+'}';}

	void addState(VisMap.State state) {
		if (states.isEmpty()) states = new SimpleList<>();
		states.add(state);
	}
	void combineState(VisMap visMap) {
		for (int i = 0; i < states.size(); i++) {
			visMap.orElse(states.get(i));
		}
	}
}