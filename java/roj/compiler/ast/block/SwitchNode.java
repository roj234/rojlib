package roj.compiler.ast.block;

import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/30 0030 16:48
 */
public class SwitchNode {
	Variable variable;
	MethodWriter block;
	List<Object> labels;
	Label location;
	int lineNumber;

	public SwitchNode(List<Object> labels) {this.labels = labels;}
}