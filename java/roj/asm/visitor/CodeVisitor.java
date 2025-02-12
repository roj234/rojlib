package roj.asm.visitor;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * Code attribute visitor
 *
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeVisitor {
	protected int bci;

	static void checkWide(byte code) {
		switch (code) {
			case RET, IINC, ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {}
			default -> throw new IllegalStateException(showOpcode(code)+"不支持WIDE指令");
		}
	}

	public CodeVisitor() {}

	public final void visitCopied(ConstantPool cp, DynByteBuf buf) { visit(cp, AsmShared.local().copy(buf)); }

	public void visit(ConstantPool cp, DynByteBuf r) {
		visitSize(r.readUnsignedShort(), r.readUnsignedShort());

		int len = r.readInt();
		visitBytecode(cp, r, len);

		visitExceptions();
		len = r.readUnsignedShort();
		while (len-- > 0) {
			visitException(r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort(), (CstClass) cp.get(r));
		}

		visitAttributes();
		len = r.readUnsignedShort();
		int wend = r.wIndex();
		while (len-- > 0) {
			String name = ((CstUTF) cp.get(r)).str();
			int length = r.readInt();
			int end = length + r.rIndex;
			r.wIndex(end);
			try {
				visitAttribute(cp, name, length, r);
				r.rIndex = end;
			} finally {
				r.wIndex(wend);
			}
		}

		visitEnd();
	}

	protected void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		int rBegin = r.rIndex;
		len += rBegin;

		byte prev = 0, code;
		try {
			while (r.rIndex < len) {
				bci = r.rIndex - rBegin;
				code = Opcodes.validateOpcode(r.readByte());

				boolean widen = prev == Opcodes.WIDE;
				if (widen) checkWide(code);
				else _visitNodePre();

				switch (code) {
					case PUTFIELD, GETFIELD, PUTSTATIC, GETSTATIC -> field(code, (CstRefField) cp.get(r));
					case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> invoke(code, (CstRef) cp.get(r));
					case INVOKEINTERFACE -> invokeItf((CstRefItf) cp.get(r), r.readShort());
					case INVOKEDYNAMIC -> invokeDyn((CstDynamic) cp.get(r), r.readUnsignedShort());
					case GOTO, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_icmpeq, IF_icmpne, IF_icmplt, IF_icmpge, IF_icmpgt, IF_icmple, IF_acmpeq, IF_acmpne, IFNULL, IFNONNULL, JSR -> jump(code, r.readShort());
					case GOTO_W, JSR_W -> jump(code, r.readInt());
					case SIPUSH -> smallNum(code, r.readShort());
					case RET -> ret(widen ? r.readShort() : r.readByte());
					case BIPUSH -> smallNum(code, r.readByte());
					case NEWARRAY -> newArray(r.readByte());
					case LDC -> ldc(LDC, cp.array(r.readUnsignedByte()));
					case LDC_W, LDC2_W -> ldc(code, cp.get(r));
					case IINC -> iinc(widen ? r.readUnsignedShort() : r.readUnsignedByte(), widen ? r.readShort() : r.readByte());
					case WIDE -> {
						if (prev == WIDE) throw new IllegalArgumentException("multi wide");
					}
					case NEW, ANEWARRAY, INSTANCEOF, CHECKCAST -> clazz(code, (CstClass) cp.get(r));
					case MULTIANEWARRAY -> multiArray((CstClass) cp.get(r), r.readUnsignedByte());
					case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> vars(code, widen ? r.readUnsignedShort() : r.readUnsignedByte());
					case TABLESWITCH -> {
						// align
						r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
						tableSwitch(r);
					}
					case LOOKUPSWITCH -> {
						r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
						lookupSwitch(r);
					}
					default -> one(code);
				}

				prev = code;
			}
		} catch (Exception e) {
			throw new IllegalStateException("Parse failed near BCI#"+bci, e);
		}
	}

	void _visitNodePre() {}

	protected void visitSize(int stackSize, int localSize) {}

	protected final boolean decompressVar(byte code) {
		String name = Opcodes.showOpcode(code);
		// xLOAD_y
		if (name.length() == 7 && name.startsWith("Load_", 1)) {
			vars((byte) Opcodes.opcodeByName().getInt(name.substring(0,5)), name.charAt(6)-'0');
			return true;
		} else if (name.length() == 8 && name.startsWith("Store_", 1)) {
			vars((byte) Opcodes.opcodeByName().getInt(name.substring(0,6)), name.charAt(7)-'0');
			return true;
		}

		return false;
	}

	protected void newArray(byte type) {}
	protected void multiArray(CstClass clz, int dimension) {}
	protected void clazz(byte code, CstClass clz) {}
	protected void iinc(int id, int count) {}
	protected void ldc(byte code, Constant c) {}
	protected void invokeDyn(CstDynamic dyn, int type) {}
	protected void invokeItf(CstRefItf itf, short argc) {}
	protected void invoke(byte code, CstRef method) {}
	protected void field(byte code, CstRefField field) {}
	protected void jump(byte code, int offset) {}
	protected void one(byte code) {}
	protected void smallNum(byte code, int value) {}
	protected void vars(byte code, int value) {}
	protected void ret(int value) {}
	protected void tableSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int low = r.readInt();
		int hig = r.readInt();
		r.rIndex += (hig - low + 1) << 2;
	}
	protected void lookupSwitch(DynByteBuf r) {
		int def = r.readInt();
		int count = r.readInt();

		r.rIndex += count << 3;
	}

	public void visitExceptions() {}
	protected void visitException(int start, int end, int handler, CstClass type) {}

	public void visitAttributes() {}
	protected void visitAttribute(ConstantPool cp, String name, int len, DynByteBuf data) {}

	public void visitEnd() {}
}