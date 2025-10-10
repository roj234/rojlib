package roj.net.rpc;

import roj.asm.ClassNode;
import roj.asm.insn.CodeWriter;
import roj.asm.type.IType;
import roj.collect.ToIntMap;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/10/16 12:24
 */
final class LoadType {
	final ToIntMap<IType> types = new ToIntMap<>();
	final ClassNode node;
	final CodeWriter clinit;

	public LoadType(ClassNode node) {
		this.node = node;
		clinit = node.newMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", "()V");
		clinit.visitSize(1, 0);
	}

	public void loadType(CodeWriter cw, IType type) {
		int fieldId = types.getInt(type);
		if (fieldId < 0) {
			clinit.ldc(type.toDesc());
			clinit.invokeS("roj/asm/type/Signature", "parseGeneric", "(Ljava/lang/CharSequence;)Lroj/asm/type/IType;");
			fieldId = node.newField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "type$" + node.fields.size(), "Lroj/asm/type/IType;");
			clinit.field(PUTSTATIC, node, fieldId);

			types.putInt(type, fieldId);
		}

		cw.field(GETSTATIC, node, fieldId);
	}
}
