package roj.compiler.plugins.struct;

import roj.asm.FieldNode;
import roj.asm.insn.CodeWriter;
import roj.compiler.api.FieldAccessHook;

/**
 * @author Roj234
 * @since 2025/10/26 03:49
 */
public final class StructField extends FieldAccessHook {
	public void writeRead(CodeWriter cw, String owner, FieldNode fn) {cw.invokeS(owner, fn.name(), "(J)"+fn.fieldType().toDesc());}
	public void writeWrite(CodeWriter cw, String owner, FieldNode fn) {cw.invokeS(owner, fn.name(), "(J"+fn.fieldType().toDesc()+")V");}
	public String toString() {return "StructFieldTransformer";}
}
