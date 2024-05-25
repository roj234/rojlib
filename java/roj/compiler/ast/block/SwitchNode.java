package roj.compiler.ast.block;

import roj.asm.visitor.Label;
import roj.compiler.asm.MethodWriter;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/30 0030 16:48
 */
public class SwitchNode {
	MethodWriter block;
	List<Object> labels;
	Label location;
}