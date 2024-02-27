package roj.exe.elf;

import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/5/30 17:34
 */
public class ElfHeader implements ElfSegment {
	public static final int TYPE_NONE = 0;
	public static final int TYPE_REL = 1;
	public static final int TYPE_EXEC = 2;
	public static final int TYPE_DYN = 3;
	public static final int TYPE_CORE = 4;
	public static final int TYPE_LO_PROC = 0xff00;
	public static final int TYPE_HI_PROC = 0xffff;


	public static final int FLG_MIPS_ARCH_1 = 0x00000000; /* -mips1 code.  */
	public static final int FLG_MIPS_ARCH_2 = 0x10000000; /* -mips2 code.  */
	public static final int FLG_MIPS_ARCH_3 = 0x20000000; /* -mips3 code.  */
	public static final int FLG_MIPS_ARCH_4 = 0x30000000; /* -mips4 code.  */
	public static final int FLG_MIPS_ARCH_5 = 0x40000000; /* -mips5 code.  */
	public static final int FLG_MIPS_ARCH_32 = 0x50000000; /* MIPS32 code.  */
	public static final int FLG_MIPS_ARCH_64 = 0x60000000; /* MIPS64 code.  */
	/* The ABI of a file. */
	public static final int FLG_MIPS_ABI_O32 = 0x00001000; /* O32 ABI.  */
	public static final int FLG_MIPS_ABI_O64 = 0x00002000; /* O32 extended for 64 bit.  */

	public static final int FLG_MIPS_NO_REORDER = 0x00000001;
	public static final int FLG_MIPS_PIC = 0x00000002;
	public static final int FLG_MIPS_C_PIC = 0x00000004;
	public static final int FLG_MIPS_ABI2 = 0x00000020;
	public static final int FLG_MIPS_OPTIONS_FIRST = 0x00000080;
	public static final int FLG_MIPS_32BITMODE = 0x00000100;
	public static final int FLG_MIPS_ABI = 0x0000f000;
	public static final int FLG_MIPS_ARCH = 0xf0000000;

	public char type;
	public char machine;
	public int version;

	public int entry;
	public int programOffset;
	public int sectionOffset;

	public int flags;

	public char headerSize;
	public char programEntrySize;
	public char programCount;
	public char sectionEntrySize;
	public char sectionCount;

	public char stringTabOffset;

	public void toByteArray(ElfFile owner, ByteList w) {
		w.putShort(type)
		 .putShort(machine)
		 .putInt(version)
		 .putInt(entry)
		 .putInt(programOffset)
		 .putInt(sectionOffset)
		 .putInt(flags)
		 .putShort(headerSize)
		 .putShort(programEntrySize)
		 .putShort(programCount)
		 .putShort(sectionEntrySize)
		 .putShort(sectionCount)
		 .putShort(stringTabOffset);
	}

	public void fromByteArray(ElfFile owner, ByteList r) {
		type = r.readChar();
		machine = r.readChar();
		version = r.readInt();
		entry = r.readInt();
		programOffset = r.readInt();
		sectionOffset = r.readInt();
		flags = r.readInt();
		headerSize = r.readChar();
		programEntrySize = r.readChar();
		programCount = r.readChar();
		sectionEntrySize = r.readChar();
		sectionCount = r.readChar();
		stringTabOffset = r.readChar();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(1024);

		sb.append("PE公共头部");
		return sb.toString();
	}
}