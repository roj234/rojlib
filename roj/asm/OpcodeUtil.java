package roj.asm;

import roj.collect.Int2IntMap;
import roj.collect.ToIntMap;

import java.util.Locale;

/**
 * @author Roj233
 * @since 2021/10/19 19:05
 */
public final class OpcodeUtil implements Opcodes {
	public static boolean canThrowRuntimeException(byte code) {
		switch (code) {
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
			// NoSuchMethodError
			case INVOKESTATIC: case INVOKEDYNAMIC:
				return true;
			default: return false;
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

	public static final String[] _Names = new String[256];
	public static final byte[] _Flags = new byte[256];

	private static ToIntMap<CharSequence> byName;
	public static ToIntMap<CharSequence> getByName() {
		if (byName == null) {
			byName = new ToIntMap<>(256);
			for (int i = 0; i < _Names.length; i++) {
				if (_Names[i] == null) continue;
				byName.putInt(_Names[i], i);
				byName.putInt(_Names[i].toUpperCase(Locale.ROOT), i);
				byName.putInt(_Names[i].toLowerCase(Locale.ROOT), i);
			}
		}
		return byName;
	}

	public static byte byId(byte code) {
		if (_Names[code&0xFF] == null) throw new IllegalStateException("Unknown bytecode 0x"+Integer.toHexString(code&0xFF));
		return code;
	}
	public static String toString0(int code) {
		String x = _Names[code&0xFF];
		if (x == null) throw new IllegalStateException("Unknown bytecode 0x"+Integer.toHexString(code&0xFF));
		return x;
	}

	public static final int CATE_MISC = 0,
		CATE_LOAD_STORE=1, CATE_LOAD_STORE_LEN=2, CATE_CONST=3, CATE_LDC=4,
		CATE_MATH=5, CATE_STACK=6, CATE_MATH_CAST=7,
		CATE_IF=8, CATE_RETURN=9, CATE_GOTO=10,
		CATE_CLASS=11, CATE_METHOD=12, CATE_FIELD=13, CATE_ARRAY_SL = 14;
	public static int category(int code) { return _Flags[code&0xFF]&0xF; }
	public static final int
		TRAIT_ZERO_ADDRESS=16,
		TRAIT_JUMP=64,
		TRAIT_ILFDA=128;
	public static int trait(int code) { return _Flags[code&0xFF]&0xF0; }
	public static int flag(int code) { return _Flags[code&0xFF]&0xFF; }

	public static void assertCate(int code, int i) { if (i != (i = category(code))) throw new IllegalArgumentException("参数错误,不支持的操作码类型/"+i+"/"+ toString0(code)); }
	public static void assertTrait(int code, int i) { if ((i & trait(code)) == 0) throw new IllegalArgumentException("参数错误,不支持的操作码特性/"+ trait(code)+"/"+ toString0(code)); }

	private static final Int2IntMap CAN_SHIFT = new Int2IntMap();
	public static int shift(int i) { return CAN_SHIFT.getOrDefaultInt(i&0xFF, 0); }

	static {
		// @=零地址
		String desc = "Nop| AConst_NULL| IConst_M1| IConst_0| IConst_1| IConst_2| IConst_3| IConst_4| IConst_5| "+
			"LConst_0| LConst_1| FConst_0| FConst_1| FConst_2| DConst_0| DConst_1| BIPush# "+
			"SIPush# Ldc# Ldc_W# Ldc2_W# ILoad# LLoad# FLoad# DLoad# ALoad# ILoad_0| ILoad_1| "+
			"ILoad_2| ILoad_3| LLoad_0| LLoad_1| LLoad_2| LLoad_3| FLoad_0| FLoad_1| FLoad_2| "+
			"FLoad_3| DLoad_0| DLoad_1| DLoad_2| DLoad_3| ALoad_0| ALoad_1| ALoad_2| ALoad_3| "+
			"IALoad| LALoad| FALoad| DALoad| AALoad| BALoad| CALoad| SALoad| IStore# LStore# "+
			"FStore# DStore# AStore# IStore_0| IStore_1| IStore_2| IStore_3| LStore_0| LStore_1| "+
			"LStore_2| LStore_3| FStore_0| FStore_1| FStore_2| FStore_3| DStore_0| DStore_1| "+
			"DStore_2| DStore_3| AStore_0| AStore_1| AStore_2| AStore_3| IAStore| LAStore| "+
			"FAStore| DAStore| AAStore| BAStore| CAStore| SAStore| Pop| Pop2| Dup| Dup_X1| "+
			"Dup_X2| Dup2| Dup2_X1| Dup2_X2| Swap| IAdd| LAdd| FAdd| DAdd| ISub| LSub| FSub| "+
			"DSub| IMul| LMul| FMul| DMul| IDiv| LDiv| FDiv| DDiv| IRem| LRem| FRem| DRem| "+
			"INeg| LNeg| FNeg| DNeg| IShL| LShL| IShR| LShR| IUshR| LUshR| IAnd| LAnd| IOr| "+
			"Lor| IXor| LXor| IInc# I2L| I2F| I2D| L2I| L2F| L2D| F2I| F2L| F2D| D2I| D2L| "+
			"D2F| I2B| I2C| I2S| LCmp| FCmpL| FCmpG| DCmpL| DCmpG| IfEq# IfNe# IfLt# IfGe# "+
			"IfGt# IfLe# If_ICmpEq# If_iCmpNe# If_iCmpLt# If_iCmpGe# If_iCmpGt# If_iCmpLe# "+
			"If_ACmpEq# If_ACmpNe# Goto# Jsr# Ret# TableSwitch# LookupSwitch# IReturn| LReturn| "+
			"FReturn| DReturn| AReturn| Return| GetStatic# PutStatic# GetField# PutField# "+
			"InvokeVirtual# InvokeSpecial# InvokeStatic# InvokeInterface# InvokeDynamic# "+
			"New# NewArray# ANewArray# ArrayLength| AThrow| CheckCast# InstanceOf# MonitorEnter| "+
			"MonitorExit| Wide# MultiANewArray# IfNull# IfNonNull# Goto_W# Jsr_W# BREAKPOINT| ";

		int j = 0;
		int i, prevI = 0;
		while (true) {
			i = desc.indexOf(' ', prevI);
			if (i < 0) break;

			_Names[j] = desc.substring(prevI, i-1);
			if (desc.charAt(i-1) == '|') _Flags[j] = TRAIT_ZERO_ADDRESS;
			j++;

			prevI = i+1;
		}

		_Names[254] = "IMPDEP1";
		_Names[255] = "IMPDEP2";

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
	private static void fset(int from, int to, int cat) {
		while (from <= to) _Flags[from++] |= cat;
	}
	private static void fshift(int base, int len) {
		base &= 0xFF;
		int data = base + (len << 8);
		for (int i = 0; i < len; i++) {
			CAN_SHIFT.put(base+i, data);
		}
	}
}
