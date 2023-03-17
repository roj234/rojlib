package roj.asm.visitor;

import roj.asm.AsmShared;
import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * Code attribute visitor
 *
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeVisitor {
	protected ConstantPool cp;
	protected int bci;

	public CodeVisitor() {}

	public final void visitAttributeBound(ConstantPool cp, DynByteBuf r) {
		r.skipBytes(6);
		visit(cp, r);
	}

	public final void visitCopied(ConstantPool cp, DynByteBuf buf) {
		visit(cp, AsmShared.local().copy(buf));
	}

	public void visit(ConstantPool cp, DynByteBuf r) {
		this.cp = cp;
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
			String name = ((CstUTF) cp.get(r)).getString();
			int length = r.readInt();
			int end = length + r.rIndex;
			r.wIndex(end);
			try {
				visitAttribute(name, length, r);
				r.rIndex = end;
			} finally {
				r.wIndex(wend);
			}
		}

		visitEnd();
	}

	public void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		int rBegin = r.rIndex;
		len += rBegin;

		byte prev = 0, code;
		while (r.rIndex < len) {
			bci = r.rIndex - rBegin;

			_visitNodePre();

			code = OpcodeUtil.byId(r.readByte());

			boolean widen = prev == Opcodes.WIDE;
			if (widen) {
				switch (code) {
					case RET: case IINC:
					case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
					case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
						break;
					default: throw new IllegalStateException("Unable to wide " + OpcodeUtil.toString0(code));
				}
			}

			switch (code) {
				case PUTFIELD:
				case GETFIELD:
				case PUTSTATIC:
				case GETSTATIC:
					field(code, (CstRefField) cp.get(r));
					break;

				case INVOKEVIRTUAL:
				case INVOKESPECIAL:
				case INVOKESTATIC:
					invoke(code, (CstRef) cp.get(r));
					break;
				case INVOKEINTERFACE:
					invoke_interface((CstRefItf) cp.get(r), r.readShort());
					break;
				case INVOKEDYNAMIC:
					invoke_dynamic((CstDynamic) cp.get(r), r.readUnsignedShort());
					break;

				case GOTO:
				case IFEQ:
				case IFNE:
				case IFLT:
				case IFGE:
				case IFGT:
				case IFLE:
				case IF_icmpeq:
				case IF_icmpne:
				case IF_icmplt:
				case IF_icmpge:
				case IF_icmpgt:
				case IF_icmple:
				case IF_acmpeq:
				case IF_acmpne:
				case IFNULL:
				case IFNONNULL:
					jump(code, r.readShort());
					break;
				case GOTO_W:
					jump(code, r.readInt());
					break;
				case SIPUSH:
					smallNum(code, r.readShort());
					break;
				case JSR:
					jsr(r.readShort());
					break;
				case JSR_W:
					jsr(r.readInt());
					break;
				case RET:
					ret(widen ? r.readShort() : r.readByte());
					break;
				case BIPUSH:
					smallNum(code, r.readByte());
					break;
				case NEWARRAY:
					newArray(r.readByte());
					break;
				case LDC:
					ldc(LDC, cp.array(r.readUnsignedByte()));
					break;
				case LDC_W:
				case LDC2_W:
					ldc(code, cp.get(r));
					break;

				case IINC:
					increase(widen ? r.readUnsignedShort() : r.readUnsignedByte(), widen ? r.readShort() : r.readByte());
					break;

				case WIDE: break;

				case NEW:
				case ANEWARRAY:
				case INSTANCEOF:
				case CHECKCAST:
					clazz(code, (CstClass) cp.get(r));
					break;

				case MULTIANEWARRAY:
					multiArray((CstClass) cp.get(r), r.readUnsignedByte());
					break;

				case ISTORE:
				case LSTORE:
				case FSTORE:
				case DSTORE:
				case ASTORE:
				case ILOAD:
				case LLOAD:
				case FLOAD:
				case DLOAD:
				case ALOAD:
					var(code, widen ? r.readUnsignedShort() : r.readUnsignedByte());
					break;
				case TABLESWITCH:
					// align
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					tableSwitch(r);
					break;
				case LOOKUPSWITCH:
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					lookupSwitch(r);
					break;
				default: one(code);
			}

			prev = code;
		}
	}

	void _visitNodePre() {}

	public void visitSize(int stackSize, int localSize) {}

	protected void newArray(byte type) {}
	protected void multiArray(CstClass clz, int dimension) {}
	protected void clazz(byte code, CstClass clz) {}
	protected void increase(int id, int count) {}
	protected void ldc(byte code, Constant c) {}
	protected void invoke_dynamic(CstDynamic dyn, int type) {}
	protected void invoke_interface(CstRefItf itf, short argc) {}
	protected void invoke(byte code, CstRef method) {}
	protected void field(byte code, CstRefField field) {}
	protected void jump(byte code, int offset) {}
	protected void one(byte code) {}
	protected void smallNum(byte code, int value) {}
	protected void var(byte code, int value) {}
	protected void jsr(int value) {}
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
	protected void visitAttribute(String name, int len, DynByteBuf data) {}

	public void visitEnd() {}
}
