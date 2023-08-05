package roj.lavac.parser;

import roj.asm.frame.node.LazyIINC;
import roj.asm.frame.node.LazyLoadStore;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeCast;
import roj.asm.util.AttributeList;
import roj.asm.util.TryCatchEntry;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.lavac.asm.Variable;
import roj.util.Helpers;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriterL extends CodeWriter {
	public CompileUnit owner;
	public CompileContext ctx;

	public MethodNode method;
	public AttributeList attributes;
	public CodeWriter codeWriter;

	public MethodWriterL(MethodNode mn) {

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

	}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		return Helpers.nonnull();
	}

	public TypeCast checkCast(IType type, Type std) {

		return Helpers.nonnull();
	}

	public void load(Variable v) { addSegment(new LazyLoadStore(v, false)); }
	public void store(Variable v) { addSegment(new LazyLoadStore(v, true)); }
	public void mutate(Variable v, int delta) { addSegment(new LazyIINC(v, delta)); }

	public boolean hasNormalEnd() {
		return false;
	}
}
