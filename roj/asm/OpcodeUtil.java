package roj.asm;

import roj.asm.cst.Constant;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.LdcInsnNode;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2021/10/19 19:05
 */
public final class OpcodeUtil {
	public static final int EXCEPTION_NONE = 0, EXCEPTION_CLASSLOADING = 1, EXCEPTION_RUNTIME = 2;

	public static int canThrowException(InsnNode b) {
		switch (b.code) {
			// ClassCastException
			case CHECKCAST:
			// NullPointerException
			case ATHROW:
			// NullPointerException
			case MONITORENTER: case MONITOREXIT:
			// NullPointerException, ArrayStoreException
			case IALOAD: case LALOAD: case FALOAD: case DALOAD:
			case AALOAD: case BALOAD: case CALOAD: case SALOAD:
			case IASTORE: case LASTORE: case FASTORE: case DASTORE:
			case AASTORE: case BASTORE: case CASTORE: case SASTORE:
			// NullPointerException
			// NoClassDefFoundError, NoSuchMethodError, AbstractMethodError ...
			case GETFIELD: case PUTFIELD:
			case INVOKEVIRTUAL: case INVOKEINTERFACE: case INVOKESPECIAL:
			// NullPointerException
			case ARRAYLENGTH:
			// NoClassDefFoundError, OutOfMemoryError, NegativeArraySizeException
			case NEW: case NEWARRAY:
			case ANEWARRAY: case MULTIANEWARRAY:
				return EXCEPTION_RUNTIME;
			// NoClassDefFoundError
			case LDC:
			case LDC_W:
				switch (((LdcInsnNode) b).c.type()) {
					case Constant.CLASS:
					case Constant.METHOD_HANDLE:
					case Constant.METHOD_TYPE:
					case Constant.DYNAMIC: return EXCEPTION_CLASSLOADING;
				}
				return EXCEPTION_NONE;
			// NoSuchMethodError
			case GETSTATIC: case PUTSTATIC: case INVOKESTATIC:
			// BootstrapMethodError
			case INVOKEDYNAMIC: return EXCEPTION_CLASSLOADING;
			default: return EXCEPTION_NONE;
		}
	}

	public static void checkWide(byte code) {
		switch (code) {
			case RET: case IINC:
			case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
			case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD: break;
			default: throw new IllegalStateException("Unable wide " + toString0(code));
		}
	}

	private static final String[] Desc = new String[256];
	private static final byte[] Cate = new byte[256];

	public static byte byId(byte code) {
		if (Desc[code&0xFF] == null) throw new IllegalStateException("Unknown bytecode 0x"+Integer.toHexString(code&0xFF));
		return code;
	}
	public static String toString0(int code) {
		String x = Desc[code&0xFF];
		if (x == null) throw new IllegalStateException("Unknown bytecode 0x"+Integer.toHexString(code&0xFF));
		return x;
	}

	public static final int CATE_MISC = 0,
		CATE_LOAD=1, CATE_STORE=2, CATE_CONST=3, CATE_LDC = 4,
		CATE_MATH=5, CATE_STACK=6, CATE_MATH_CAST=7,
		CATE_IF=8, CATE_RETURN=9, CATE_GOTO=10,
		CATE_CLASS=11, CATE_METHOD=12, CATE_FIELD=13;
	public static int category(int code) {
		return Cate[code&0xFF]&0xF;
	}
	public static final int
		TRAIT_ZERO_ADDRESS=16,
		TRAIT_LOAD_STORE_LEN=32,
		TRAIT_JUMP=64,
		TRAIT_LOAD_INT=128;
	public static int trait(int code) {
		return Cate[code&0xFF]&0xF0;
	}

	static {
		// @=零地址
		String desc = "NOP| ACONST_NULL| ICONST_M1| ICONST_0| ICONST_1| ICONST_2| ICONST_3| ICONST_4| ICONST_5| "+
			"LCONST_0| LCONST_1| FCONST_0| FCONST_1| FCONST_2| DCONST_0| DCONST_1| BIPUSH# "+
			"SIPUSH# LDC# LDC_W# LDC2_W# ILOAD# LLOAD# FLOAD# DLOAD# ALOAD# ILOAD_0| ILOAD_1| "+
			"ILOAD_2| ILOAD_3| LLOAD_0| LLOAD_1| LLOAD_2| LLOAD_3| FLOAD_0| FLOAD_1| FLOAD_2| "+
			"FLOAD_3| DLOAD_0| DLOAD_1| DLOAD_2| DLOAD_3| ALOAD_0| ALOAD_1| ALOAD_2| ALOAD_3| "+
			"IALOAD| LALOAD| FALOAD| DALOAD| AALOAD| BALOAD| CALOAD| SALOAD| ISTORE# LSTORE# "+
			"FSTORE# DSTORE# ASTORE# ISTORE_0| ISTORE_1| ISTORE_2| ISTORE_3| LSTORE_0| LSTORE_1| "+
			"LSTORE_2| LSTORE_3| FSTORE_0| FSTORE_1| FSTORE_2| FSTORE_3| DSTORE_0| DSTORE_1| "+
			"DSTORE_2| DSTORE_3| ASTORE_0| ASTORE_1| ASTORE_2| ASTORE_3| IASTORE| LASTORE| "+
			"FASTORE| DASTORE| AASTORE| BASTORE| CASTORE| SASTORE| POP| POP2| DUP| DUP_X1| "+
			"DUP_X2| DUP2| DUP2_X1| DUP2_X2| SWAP| IADD| LADD| FADD| DADD| ISUB| LSUB| FSUB| "+
			"DSUB| IMUL| LMUL| FMUL| DMUL| IDIV| LDIV| FDIV| DDIV| IREM| LREM| FREM| DREM| "+
			"INEG| LNEG| FNEG| DNEG| ISHL| LSHL| ISHR| LSHR| IUSHR| LUSHR| IAND| LAND| IOR| "+
			"LOR| IXOR| LXOR| IINC| I2L| I2F| I2D| L2I| L2F| L2D| F2I| F2L| F2D| D2I| D2L| "+
			"D2F| I2B| I2C| I2S| LCMP| FCMPL| FCMPG| DCMPL| DCMPG| IFEQ# IFNE# IFLT# IFGE# "+
			"IFGT# IFLE# IF_icmpeq# IF_icmpne# IF_icmplt# IF_icmpge# IF_icmpgt# IF_icmple# "+
			"IF_acmpeq# IF_acmpne# GOTO# JSR# RET# TABLESWITCH# LOOKUPSWITCH# IRETURN| LRETURN| "+
			"FRETURN| DRETURN| ARETURN| RETURN| GETSTATIC# PUTSTATIC# GETFIELD# PUTFIELD# "+
			"INVOKEVIRTUAL# INVOKESPECIAL# INVOKESTATIC# INVOKEINTERFACE# INVOKEDYNAMIC# "+
			"NEW# NEWARRAY# ANEWARRAY# ARRAYLENGTH| ATHROW| CHECKCAST# INSTANCEOF# MONITORENTER| "+
			"MONITOREXIT| WIDE# MULTIANEWARRAY# IFNULL# IFNONNULL# GOTO_W# JSR_W# BREAKPOINT| ";

		int j = 0;
		int i, prevI = 0;
		while (true) {
			i = desc.indexOf(' ', prevI);
			if (i < 0) break;

			Desc[j] = desc.substring(prevI, i-1);
			if (desc.charAt(i-1) == '|') Cate[j] = TRAIT_ZERO_ADDRESS;
			j++;

			prevI = i+1;
		}

		Desc[254] = "IMPDEP1";
		Desc[255] = "IMPDEP2";

		fset(1,17, CATE_CONST);
		fset(18,20, CATE_LDC);
		fset(21,45, CATE_LOAD);
		fset(54,78, CATE_STORE);
		fset(87,95, CATE_STACK);
		fset(96,131, CATE_MATH);
		fset(133, 147, CATE_MATH_CAST);
		fset(153, 166, CATE_IF|TRAIT_JUMP);
		fset(172, 177, CATE_RETURN);
		fset(178, 181, CATE_FIELD);
		fset(182, 185, CATE_METHOD);
		Cate[GOTO&0xFF] |= CATE_GOTO|TRAIT_JUMP;
		Cate[GOTO_W&0xFF] |= CATE_GOTO|TRAIT_JUMP;
		Cate[NEW&0xFF] |= CATE_CLASS;
		Cate[CHECKCAST&0xFF] |= CATE_CLASS;
		Cate[INSTANCEOF&0xFF] |= CATE_CLASS;
		Cate[ANEWARRAY&0xFF] |= CATE_CLASS;
		Cate[IFNULL&0xFF] |= CATE_IF|TRAIT_JUMP;
		Cate[IFNONNULL&0xFF] |= CATE_IF|TRAIT_JUMP;

		fset(16,17, TRAIT_LOAD_INT);
		fset(21, 25, TRAIT_LOAD_STORE_LEN);
		fset(54, 58, TRAIT_LOAD_STORE_LEN);
	}
	private static void fset(int from, int to, int cat) {
		while (from <= to) {
			Cate[from++] |= cat;
		}
	}
}
