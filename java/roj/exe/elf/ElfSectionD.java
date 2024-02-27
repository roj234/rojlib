package roj.exe.elf;

import roj.util.ByteList;

/**
 * Section Header / Data Section
 *
 * @author Roj233
 * @since 2022/5/30 17:34
 */
public class ElfSectionD implements ElfSegment {
	public static final int SIZE = 40;

	public static final int DT_NULL = 0;
	public static final int DT_PROGRAM_BITS = 1;
	public static final int DT_SYMBOL_TABLE = 2;
	public static final int DT_STRING_TABLE = 3;
	public static final int DT_RELOCATION_ADDEND = 4;
	public static final int DT_HASH = 5;
	public static final int DT_DYNAMIC = 6;
	public static final int DT_NOTE = 7;
	public static final int DT_NO_BITS = 8;
	public static final int DT_RELOCATION = 9;
	public static final int DT_RESERVED = 10;
	public static final int DT_DYNAMIC_SYMBOL = 11;
	public static final int DT_NUM = 12;

	public static final int DT_LO_PROC = 0x70000000;
	public static final int DT_HI_PROC = 0x7fffffff;
	public static final int DT_LO_USER = 0x80000000;
	public static final int DT_HI_USER = 0xffffffff;

	public static final int DT_MIPS_LIST = 0x70000000;
	public static final int DT_MIPS_CONFLICT = 0x70000002;
	public static final int DT_MIPS_GP_TABLE = 0x70000003;
	public static final int DT_MIPS_U_CODE = 0x70000004;


	public static final int DFLG_WRITE = 0x1;
	public static final int DFLG_ALLOC = 0x2;
	public static final int DFLG_EXEC = 0x4;

	public static final int DFLG_MASK_PROC = 0xf0000000;

	public static final int DFLG_MIPS_GP_RELOCATION = 0x10000000;

	public int nameIndex;
	public int type;
	public int flags;
	public int address;
	public int offset;
	public int length;
	public int link;
	public int info;
	public int align;
	public int entrySize;

	public void toByteArray(ElfFile owner, ByteList w) {
		w.putInt(nameIndex).putInt(type).putInt(flags).putInt(address).putInt(offset).putInt(length).putInt(link).putInt(info).putInt(align).putInt(entrySize);
	}

	public void fromByteArray(ElfFile owner, ByteList r) {
		nameIndex = r.readInt();
		type = r.readInt();
		flags = r.readInt();
		address = r.readInt();
		offset = r.readInt();
		length = r.readInt();
		link = r.readInt();
		info = r.readInt();
		align = r.readInt();
		entrySize = r.readInt();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(1024);

		sb.append("PE公共头部");
		return sb.toString();
	}
}