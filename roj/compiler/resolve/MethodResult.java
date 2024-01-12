package roj.compiler.resolve;

import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.collect.IntMap;
import roj.compiler.JavaLexer;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:59
 */
public final class MethodResult {
	public MethodNode method;
	public boolean directVarargCall;
	public IType[] desc, exception;

	public IntMap<Object> namedParams;

	public int distance;
	public Object[] error;

	public void appendError(CharList sb) {
		String translate = JavaLexer.translate.translate(error == null || error.length == 0 ? "typeCast.error."+distance : "typeCast.error."+distance+":"+error[0]+":"+error[1]);
		sb.append(translate);
	}
}