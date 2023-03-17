package roj.lavac.parser;

import roj.asm.frame.MethodPoet;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.util.Helpers;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodPoetL extends MethodPoet {
	public CompileUnit owner;
	public CompileContext ctx;

	public MethodPoetL(MethodNode mn) {
		super(null);
		init(mn);
	}

	public JavaLexer getLexer() {
		return Helpers.nonnull();
	}

	public void useVariable(Variable name) {


	}

	public void assignVariable(Variable name) {

	}

	public AnnVal getConstant(Variable name) {
		return null;
	}

	public Variable getVariable(String name) {
		return null;
	}

	public void enterCatcher(String type) {
		stack.clear();
		stack.add(obj(type));
	}
}
