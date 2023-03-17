package roj.mildwind.asm;

import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2023/6/12 0012 16:15
 */
public final class JsFunctionUncompiled extends JsFunction {
	private final int methodId, depth;
	private final JsMethodWriter compiler;

	public JsFunctionUncompiled(JsMethodWriter compiler, int methodId, int depth) {
		this.compiler = compiler;
		this.methodId = methodId;
		this.depth = depth;
	}

	public final JsObject _invoke(JsObject self, Arguments arg) {
		if (depth > 0) throw new IllegalStateException("JsFunctionUncompiled("+depth+") cannot be called without closure");

		return compiler.compile().copy(methodId)._invoke(self, arg);
	}

	public JsFunction _prepareClosure(JsObject[][] prevClosure, JsObject[] currClosure) {
		return compiler.compile().copy(methodId)._prepareClosure(prevClosure, currClosure);
	}

	//public boolean hasTCO() { return compiler.hasTCO(methodId); }
}
