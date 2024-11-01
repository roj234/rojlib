package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.compiler.api.FieldWriteReplace;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;

/**
 * @author Roj234
 * @since 2024/7/4 0004 14:22
 */
public final class FieldBridge extends FieldWriteReplace {
	private final IClass owner;
	private int readAccessor = -1, writeAccessor = -1;
	private boolean exist;

	public FieldBridge(CompileUnit owner) {
		this.owner = owner;
		this.exist = true;
	}
	public FieldBridge(IClass owner, int readAccessor, int writeAccessor) {
		this.owner = owner;
		this.readAccessor = readAccessor;
		this.writeAccessor = writeAccessor;
	}

	@Override
	public String toString() {return "FWR<Generic FieldBridge>";}

	@Override
	public void writeRead(CodeWriter cw, String owner, FieldNode fn) {
		if (exist && cw.mn.owner.equals(this.owner.name())) {
			super.writeRead(cw, owner, fn);
		} else {
			if (readAccessor < 0) {
				if (readAccessor == -2) {
					LocalContext.get().report(Kind.ERROR, "symbol.error.field.notReadable", owner, fn.name());
					return;
				}
				var _realOwner = (CompileUnit) this.owner;
				Type type = fn.fieldType();

				readAccessor = _realOwner.methods.size();

				CodeWriter cw1;
				if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
					cw1 = _realOwner.newMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, _realOwner.getNextAccessorName(), "()"+type.toDesc());
					cw1.visitSize(type.length(), 0);
					cw1.field(Opcodes.GETSTATIC, owner, fn.name(), fn.rawDesc());
				} else {
					cw1 = _realOwner.newMethod(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, _realOwner.getNextAccessorName(), "()"+type.toDesc());
					cw1.visitSize(type.length(), 1);
					cw1.one(Opcodes.ALOAD_0);
					cw1.field(Opcodes.GETFIELD, owner, fn.name(), fn.rawDesc());
				}

				cw1.return_(type);
				cw1.finish();
			}
			bridge(cw, fn, readAccessor);
		}
	}
	@Override
	public void writeWrite(CodeWriter cw, String owner, FieldNode fn) {
		if (exist && cw.mn.owner.equals(this.owner.name())) {
			super.writeWrite(cw, owner, fn);
		} else {
			if (writeAccessor < 0) {
				var _realOwner = (CompileUnit) this.owner;
				Type type = fn.fieldType();

				writeAccessor = _realOwner.methods.size();

				CodeWriter cw1;
				if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
					cw1 = _realOwner.newMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, _realOwner.getNextAccessorName(), "("+type.toDesc()+")V");
					cw1.visitSize(type.length(), type.length());
					cw1.varLoad(type, 0);
					cw1.field(Opcodes.PUTSTATIC, owner, fn.name(), fn.rawDesc());
				} else {
					cw1 = _realOwner.newMethod(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, _realOwner.getNextAccessorName(), "("+type.toDesc()+")V");
					cw1.visitSize(type.length()+1, type.length()+1);
					cw1.one(Opcodes.ALOAD_0);
					cw1.varLoad(type, 1);
					cw1.field(Opcodes.PUTFIELD, owner, fn.name(), fn.rawDesc());
				}
				cw1.one(Opcodes.RETURN);
				cw1.finish();
			}
			bridge(cw, fn, writeAccessor);
		}
	}
	private void bridge(CodeWriter cw, FieldNode fn, int id) {
		var opcode = (fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
		cw.invoke(opcode, this.owner, id);
	}
}