package roj.asm;

import org.intellij.lang.annotations.MagicConstant;
import roj.collect.Int2IntMap;
import roj.collect.ToIntMap;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2021/1/3 15:59
 */
public final class Opcodes {
	// region JVM Opcodes
	public static final byte NOP = 0x00,
	// Variable Construct
	ACONST_NULL = 0x01,
	ICONST_M1 = 0x02, ICONST_0 = 0x03, ICONST_1 = 0x04, ICONST_2 = 0x05, ICONST_3 = 0x06, ICONST_4 = 0x07, ICONST_5 = 0x08,
	LCONST_0 = 0x09, LCONST_1 = 0x0a,
	FCONST_0 = 0x0b, FCONST_1 = 0x0c, FCONST_2 = 0x0d,
	DCONST_0 = 0x0e, DCONST_1 = 0x0f,

	BIPUSH = 0x10, SIPUSH = 0x11, LDC = 0x12, LDC_W = 0x13, LDC2_W = 0x14,

	// Variable Load
	ILOAD = 0x15, LLOAD = 0x16, FLOAD = 0x17, DLOAD = 0x18, ALOAD = 0x19,

	ILOAD_0 = 0x1a, ILOAD_1 = 0x1b, ILOAD_2 = 0x1c, ILOAD_3 = 0x1d,
	LLOAD_0 = 0x1e, LLOAD_1 = 0x1f, LLOAD_2 = 0x20, LLOAD_3 = 0x21,
	FLOAD_0 = 0x22, FLOAD_1 = 0x23, FLOAD_2 = 0x24, FLOAD_3 = 0x25,
	DLOAD_0 = 0x26, DLOAD_1 = 0x27, DLOAD_2 = 0x28, DLOAD_3 = 0x29,
	ALOAD_0 = 0x2a, ALOAD_1 = 0x2b, ALOAD_2 = 0x2c, ALOAD_3 = 0x2d,

	// ArrayGet LOAD
	IALOAD = 0x2e, LALOAD = 0x2f, FALOAD = 0x30, DALOAD = 0x31, AALOAD = 0x32, BALOAD = 0x33, CALOAD = 0x34, SALOAD = 0x35,

	// Variable Store
	ISTORE = 0x36, LSTORE = 0x37, FSTORE = 0x38, DSTORE = 0x39, ASTORE = 0x3a,

	ISTORE_0 = 0x3b, ISTORE_1 = 0x3c, ISTORE_2 = 0x3d, ISTORE_3 = 0x3e,
	LSTORE_0 = 0x3f, LSTORE_1 = 0x40, LSTORE_2 = 0x41, LSTORE_3 = 0x42,
	FSTORE_0 = 0x43, FSTORE_1 = 0x44, FSTORE_2 = 0x45, FSTORE_3 = 0x46,
	DSTORE_0 = 0x47, DSTORE_1 = 0x48, DSTORE_2 = 0x49, DSTORE_3 = 0x4a,
	ASTORE_0 = 0x4b, ASTORE_1 = 0x4c, ASTORE_2 = 0x4d, ASTORE_3 = 0x4e,

	IASTORE = 0x4f, LASTORE = 0x50, FASTORE = 0x51, DASTORE = 0x52, AASTORE = 0x53, BASTORE = 0x54, CASTORE = 0x55, SASTORE = 0x56,

	// Stack
	POP = 0X57, POP2 = 0X58, DUP = 0X59, DUP_X1 = 0X5A, DUP_X2 = 0X5B, DUP2 = 0X5C, DUP2_X1 = 0X5D, DUP2_X2 = 0X5E, SWAP = 0X5F,

	// Math
	IADD = 0X60, LADD = 0X61, FADD = 0X62, DADD = 0X63,
	ISUB = 0X64, LSUB = 0X65, FSUB = 0X66, DSUB = 0X67,
	IMUL = 0X68, LMUL = 0X69, FMUL = 0X6A, DMUL = 0X6B,
	IDIV = 0X6C, LDIV = 0X6D, FDIV = 0X6E, DDIV = 0X6F,
	IREM = 0X70, LREM = 0X71, FREM = 0X72, DREM = 0X73,
	INEG = 0X74, LNEG = 0X75, FNEG = 0X76, DNEG = 0X77,
	ISHL = 0X78, LSHL = 0X79,
	ISHR = 0X7A, LSHR = 0X7B,
	IUSHR = 0X7C, LUSHR = 0X7D,
	IAND = 0X7E, LAND = 0X7F;

	/**
	 * 进行大小的比较时请&0xFF
	 */
	public static final byte IOR = (byte) 0X80, LOR = (byte) 0X81,
		IXOR = (byte) 0X82, LXOR = (byte) 0X83,
		IINC = (byte) 0X84,
		I2L = (byte) 0X85, I2F = (byte) 0X86, I2D = (byte) 0X87,
		L2I = (byte) 0X88, L2F = (byte) 0X89, L2D = (byte) 0X8A,
		F2I = (byte) 0X8B, F2L = (byte) 0X8C, F2D = (byte) 0X8D,
		D2I = (byte) 0X8E, D2L = (byte) 0X8F, D2F = (byte) 0X90,
		I2B = (byte) 0X91, I2C = (byte) 0X92, I2S = (byte) 0X93,
		LCMP = (byte) 0X94, FCMPL = (byte) 0X95, FCMPG = (byte) 0X96, DCMPL = (byte) 0X97, DCMPG = (byte) 0X98,

	// Condition / Jump
	IFEQ = (byte) 0x99, IFNE = (byte) 0x9a, IFLT = (byte) 0x9b, IFGE = (byte) 0x9c, IFGT = (byte) 0x9d, IFLE = (byte) 0x9e,
	IF_icmpeq = (byte) 0x9f, IF_icmpne = (byte) 0xa0, IF_icmplt = (byte) 0xa1, IF_icmpge = (byte) 0xa2, IF_icmpgt = (byte) 0xa3, IF_icmple = (byte) 0xa4,
	IF_acmpeq = (byte) 0xa5, IF_acmpne = (byte) 0xa6,
	GOTO = (byte) 0xa7,
	JSR = (byte) 0xa8, RET = (byte) 0xa9,
	TABLESWITCH = (byte) 0xaa, LOOKUPSWITCH = (byte) 0xab,

	// Return
	IRETURN = (byte) 0xac, LRETURN = (byte) 0xad, FRETURN = (byte) 0xae, DRETURN = (byte) 0xaf, ARETURN = (byte) 0xb0, RETURN = (byte) 0xb1,

	// Field
	GETSTATIC = (byte) 0xb2, PUTSTATIC = (byte) 0xb3, GETFIELD = (byte) 0xb4, PUTFIELD = (byte) 0xb5,

	// Invoke
	INVOKEVIRTUAL = (byte) 0xb6, INVOKESPECIAL = (byte) 0xb7, INVOKESTATIC = (byte) 0xb8, INVOKEINTERFACE = (byte) 0xb9, INVOKEDYNAMIC = (byte) 0xba,

	// New
	NEW = (byte) 0xbb, NEWARRAY = (byte) 0xbc, ANEWARRAY = (byte) 0xbd,

	ARRAYLENGTH = (byte) 0xbe, ATHROW = (byte) 0xbf, CHECKCAST = (byte) 0xc0, INSTANCEOF = (byte) 0xc1,
	MONITORENTER = (byte) 0xc2, MONITOREXIT = (byte) 0xc3,
	WIDE = (byte) 0xc4,
	MULTIANEWARRAY = (byte) 0xc5,

	IFNULL = (byte) 0xc6, IFNONNULL = (byte) 0xc7,

	GOTO_W = (byte) 0xc8, JSR_W = (byte) 0xc9,

	BREAKPOINT = (byte) 0xca;
	// endregion
	// region access modifier
	public static final char
		ACC_PUBLIC = 0x0001,
		ACC_PRIVATE = 0x0002,
		ACC_PROTECTED = 0x0004,
		ACC_STATIC = 0x0008,
		ACC_FINAL = 0x0010,
		ACC_SUPER = 0x0020, ACC_SYNCHRONIZED = 0x0020,
		ACC_VOLATILE = 0x0040, ACC_BRIDGE = 0x0040,
		ACC_TRANSIENT = 0x0080, ACC_VARARGS = 0x0080,
		ACC_NATIVE = 0x0100,
		ACC_INTERFACE = 0x0200,
		ACC_ABSTRACT = 0x0400,
		ACC_STRICT = 0x0800,
		ACC_SYNTHETIC = 0x1000,
		ACC_ANNOTATION = 0x2000,
		ACC_ENUM = 0x4000,
		ACC_MODULE = 0x8000,
		ACC_OPEN = 0x0020, ACC_TRANSITIVE = 0x0020,
		ACC_MANDATED = 0x8000,
		ACC_STATIC_PHASE = 0x0040;

	private static final String[][] ACC_TOSTRING = new String[][] {
		/**
		 * Class_Acc_String
		 * Synthetic Modifiers:
		 * ACC_SUPER	    0x0020	Treat superclass methods specially when invoked by the invokespecial instruction.
		*/
		{ "public", null, null, null, "final", "/*super*/", null, null, null, "interface", "abstract", null, "/*synthetic*/", "@interface", "enum", "module" },
		/**
		 * Field_Acc_String
		 * Synthetic Modifiers:
		 * ACC_ENUM	        0x4000	Declared as an element of an enum.
		*/
		{ "public", "private", "protected", "static", "final", null, "volatile", "transient", null, null, null, null, "/*synthetic*/", null, "/*enum*/" },
		/**
		 * Method_Acc_String
		 * Synthetic Modifiers:
		 * ACC_BRIDGE	    0x0040	A bridge method, generated by the compiler.
		 * ACC_VARARGS	    0x0080	Declared with variable number of arguments.
		 */
		{ "public", "private", "protected", "static", "final", "synchronized", "/*bridge*/", "/*varargs*/", "native", null, "abstract", "strictfp", "/*synthetic*/" },
		/**
		 * InnerClass_Acc_String
		 */
		{ "public", "private", "protected", "static", "final", null, null, null, null, "interface", "abstract", null, "/*synthetic*/", "@interface", "enum" },
		/**
		 * ACC_OPEN         0x0020  Indicates that this module is open.
		 * ACC_TRANSITIVE   0x0020	Indicates that any module which depends on the current module, implicitly declares a dependence on the module indicated by this entry. ('转移性')
		 * ACC_STATIC_PHASE	0x0040	Mandatory in the static phase, 'compile', but is optional in the dynamic phase, 'run'.
		 * ACC_SYNTHETIC	0x1000	Declared synthetic; not present in the source code.
		 * ACC_MANDATED     0x8000  Indicates that this dependence was implicitly declared in the source of the module declaration.
		 */
		{ null, null, null, null, null, "open | transitive", "static", null, null, null, null, null, "/*synthetic*/", null, null, "mandated" }
	};
	public static final int ACC_SHOW_CLASS = 0, ACC_SHOW_FIELD = 1, ACC_SHOW_PARAM = 1, ACC_SHOW_METHOD = 2, ACC_SHOW_INNERCLASS = 3, ACC_SHOW_MODULE = 4;

	public static String showModifiers(int modifier, @MagicConstant(intValues = {ACC_SHOW_CLASS,ACC_SHOW_FIELD,ACC_SHOW_PARAM,ACC_SHOW_METHOD,ACC_SHOW_INNERCLASS,ACC_SHOW_MODULE}) int type) { return showModifiers(modifier, type, new CharList()).toStringAndFree(); }
	public static <T extends Appendable> T showModifiers(int modifier, int type, T sb) { return showModifiers(modifier, ACC_TOSTRING[type], sb); }
	public static <T extends Appendable> T showModifiers(int modifier, String[] names, T sb) {
		try {
			for (int i = 0; i < names.length; i++) {
				if ((modifier & (1 << i)) != 0) {
					String s = names[i];
					if (s != null) sb.append(s).append(' ');
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}
	// endregion
	// region 操作码名称
	private static final int OPCODE_COUNT = 201;
	private static final String[] _Names = new String[256];
	public static byte validateOpcode(byte code) {
		if (_Names[code&0xFF] == null) throw new IllegalStateException("Unknown bytecode 0x"+Integer.toHexString(code&0xFF));
		return code;
	}
	public static String showOpcode(int code) {return _Names[code&0xFF];}

	private static ToIntMap<CharSequence> byName;
	public static ToIntMap<CharSequence> opcodeByName() {
		if (byName == null) {
			byName = new ToIntMap<>(OPCODE_COUNT);
			for (int i = 0; i < _Names.length; i++) {
				if (_Names[i] == null) continue;
				byName.putInt(_Names[i], i);
				byName.putInt(_Names[i].toUpperCase(Locale.ROOT), i);
				byName.putInt(_Names[i].toLowerCase(Locale.ROOT), i);
			}
		}
		return byName;
	}
	// endregion
	// region 操作码分类
	private static final byte[] _Flags = new byte[256];
	public static int flag(int code) { return _Flags[code&0xFF]&0xFF; }

	public static final int CATE_MISC = 0,
		CATE_LOAD_STORE=1, CATE_LOAD_STORE_LEN=2, CATE_CONST=3, CATE_LDC=4,
		CATE_MATH=5, CATE_STACK=6, CATE_MATH_CAST=7,
		CATE_IF=8, CATE_RETURN=9, CATE_GOTO=10,
		CATE_CLASS=11, CATE_METHOD=12, CATE_FIELD=13, CATE_ARRAY_SL = 14;
	public static int category(int code) { return _Flags[code&0xFF]&0xF; }
	public static void assertCate(int code, int i) { if (i != (i = category(code))) throw new IllegalArgumentException("参数错误,不支持的操作码类型/"+i+"/"+showOpcode(code)); }

	public static final int TRAIT_ZERO_ADDRESS=16, TRAIT_JUMP=64, TRAIT_ILFDA=128;
	public static int trait(int code) { return _Flags[code&0xFF]&0xF0; }
	public static void assertTrait(int code, int i) { if ((i & trait(code)) == 0) throw new IllegalArgumentException("参数错误,不支持的操作码特性/"+trait(code)+"/"+showOpcode(code)); }

	private static final Int2IntMap CAN_SHIFT = new Int2IntMap();
	public static int shift(int i) { return CAN_SHIFT.getOrDefaultInt(i&0xFF, 0); }
	// endregion

	public static boolean exceptionCheck(byte code) {
		return switch (code) {
			// ClassCastException
			case CHECKCAST,
					// NullPointerException
					ATHROW, ARRAYLENGTH, MONITORENTER, MONITOREXIT,
					// NullPointerException, ArrayStoreException
					IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
					IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
					// LinkageError for Load_Dynamic
					LDC, LDC_W, LDC2_W,
					// NullPointerException | LinkageError
					GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC,
					// NullPointerException | ExceptionInExecution | LinkageError
					INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESPECIAL,
					// ExceptionInExecution | LinkageError
					INVOKESTATIC, INVOKEDYNAMIC,
					// OutOfMemoryError | LinkageError
					NEW, INSTANCEOF,
					// OutOfMemoryError | NegativeArraySizeException | LinkageError
					NEWARRAY, ANEWARRAY, MULTIANEWARRAY -> true;
			default -> false;
		};
	}

	static {
		// @=零地址
		String desc = "Nop AConst_NULL IConst_M1 IConst_0 IConst_1 IConst_2 IConst_3 IConst_4 IConst_5 "+
			"LConst_0 LConst_1 FConst_0 FConst_1 FConst_2 DConst_0 DConst_1 BIPush# "+
			"SIPush# Ldc# Ldc_W# Ldc2_W# ILoad# LLoad# FLoad# DLoad# ALoad# ILoad_0 ILoad_1 "+
			"ILoad_2 ILoad_3 LLoad_0 LLoad_1 LLoad_2 LLoad_3 FLoad_0 FLoad_1 FLoad_2 "+
			"FLoad_3 DLoad_0 DLoad_1 DLoad_2 DLoad_3 ALoad_0 ALoad_1 ALoad_2 ALoad_3 "+
			"IALoad LALoad FALoad DALoad AALoad BALoad CALoad SALoad IStore# LStore# "+
			"FStore# DStore# AStore# IStore_0 IStore_1 IStore_2 IStore_3 LStore_0 LStore_1 "+
			"LStore_2 LStore_3 FStore_0 FStore_1 FStore_2 FStore_3 DStore_0 DStore_1 "+
			"DStore_2 DStore_3 AStore_0 AStore_1 AStore_2 AStore_3 IAStore LAStore "+
			"FAStore DAStore AAStore BAStore CAStore SAStore Pop Pop2 Dup Dup_X1 "+
			"Dup_X2 Dup2 Dup2_X1 Dup2_X2 Swap IAdd LAdd FAdd DAdd ISub LSub FSub "+
			"DSub IMul LMul FMul DMul IDiv LDiv FDiv DDiv IRem LRem FRem DRem "+
			"INeg LNeg FNeg DNeg IShL LShL IShR LShR IUshR LUshR IAnd LAnd IOr "+
			"Lor IXor LXor IInc# I2L I2F I2D L2I L2F L2D F2I F2L F2D D2I D2L "+
			"D2F I2B I2C I2S LCmp FCmpL FCmpG DCmpL DCmpG IfEq# IfNe# IfLt# IfGe# "+
			"IfGt# IfLe# If_ICmpEq# If_iCmpNe# If_iCmpLt# If_iCmpGe# If_iCmpGt# If_iCmpLe# "+
			"If_ACmpEq# If_ACmpNe# Goto# Jsr# Ret# TableSwitch# LookupSwitch# IReturn LReturn "+
			"FReturn DReturn AReturn Return GetStatic# PutStatic# GetField# PutField# "+
			"InvokeVirtual# InvokeSpecial# InvokeStatic# InvokeInterface# InvokeDynamic# "+
			"New# NewArray# ANewArray# ArrayLength AThrow CheckCast# InstanceOf# MonitorEnter "+
			"MonitorExit Wide# MultiANewArray# IfNull# IfNonNull# Goto_W# Jsr_W#";

		int j = 0;
		int i, prevI = 0;
		while (true) {
			i = desc.indexOf(' ', prevI);
			if (i < 0) break;

			int k;
			if (desc.charAt(k = (i - 1)) != '#') {
				k = i;
				_Flags[j] = TRAIT_ZERO_ADDRESS;
			}
			_Names[j++] = desc.substring(prevI, k);

			prevI = i+1;
		}

		fset(1,17, CATE_CONST);
		fset(18,20, CATE_LDC);
		fset(21, 25, CATE_LOAD_STORE_LEN);
		fset(26,45, CATE_LOAD_STORE);
		fset(54, 58, CATE_LOAD_STORE_LEN);
		fset(59,78, CATE_LOAD_STORE);
		fset(87,95, CATE_STACK);
		fset(96,131, CATE_MATH);
		fset(133, 147, CATE_MATH_CAST);
		fset(153, 166, CATE_IF|TRAIT_JUMP);
		fset(172, 177, CATE_RETURN);
		fset(178, 181, CATE_FIELD);
		fset(182, 185, CATE_METHOD);
		_Flags[GOTO&0xFF] |= CATE_GOTO|TRAIT_JUMP;
		_Flags[GOTO_W&0xFF] |= CATE_GOTO|TRAIT_JUMP;
		_Flags[NEW&0xFF] |= CATE_CLASS;
		_Flags[CHECKCAST&0xFF] |= CATE_CLASS;
		_Flags[INSTANCEOF&0xFF] |= CATE_CLASS;
		_Flags[ANEWARRAY&0xFF] |= CATE_CLASS;
		_Flags[IFNULL&0xFF] |= CATE_IF|TRAIT_JUMP;
		_Flags[IFNONNULL&0xFF] |= CATE_IF|TRAIT_JUMP;

		//fset(16,17, TRAIT_LOAD_INT);
		fset(46, 53, CATE_ARRAY_SL);
		fset(79, 86, CATE_ARRAY_SL);
		fset(1,15, TRAIT_ILFDA);
		fset(21,86, TRAIT_ILFDA);
		fset(96,152, TRAIT_ILFDA);
		fset(172,177, TRAIT_ILFDA);

		// ILFDA
		fshift(IRETURN, 6);
		fshift(ILOAD, 5);
		fshift(ISTORE, 5);
		fshift(IADD, 4);
		fshift(ISUB, 4);
		fshift(IMUL, 4);
		fshift(IDIV, 4);
		fshift(IREM, 4);
		fshift(INEG, 4);
		fshift(ISHL, 2);
		fshift(ISHR, 2);
		fshift(IUSHR, 2);
		fshift(IAND, 2);
		fshift(IOR, 2);
		fshift(IXOR, 2);
	}
	private static void fset(int from, int to, int cat) { while (from <= to) _Flags[from++] |= cat; }
	private static void fshift(int base, int len) {
		base &= 0xFF;
		int data = base + (len << 8);
		for (int i = 0; i < len; i++) {
			CAN_SHIFT.put(base+i, data);
		}
	}

	public static void registerOpcode(int code, String name, int flags, int shift) {
		_Names[code] = name;
		_Flags[code] = (byte) flags;
		byName = null;
		if (shift > 0) fshift(code, shift);
	}
}