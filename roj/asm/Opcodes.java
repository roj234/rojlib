package roj.asm;

/**
 * @author Roj234
 * @see //my.oschina.net/xionghui/blog/325563
 * @since 2021/1/3 15:59
 */
public interface Opcodes {
	byte NOP = 0x00,

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
	byte IOR = (byte) 0X80, LOR = (byte) 0X81,
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

	BREAKPOINT = (byte) 0xca, IMPDEP1 = (byte) 0xfe, IMPDEP2 = (byte) 0xff;
}