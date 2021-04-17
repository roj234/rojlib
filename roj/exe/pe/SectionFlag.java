package roj.exe.pe;

import java.util.EnumSet;

public enum SectionFlag {
	NO_PADDING(0x8),

	CODE(0x20), INITIALIZED_DATA(0x40), UNINITIALIZED_DATA(0x80),

	LNK_OTHER(0x100), LNK_INFO(0x200), LNK_REMOVE(0x00000800), LNK_COMDAT(0x00001000), LNK_PRE(0x00008000), LNK_N_RELOC_OVERFLOW(0x01000000),

	MEM_PURGEABLE(0x00010000), MEM_16BIT(0x00020000), MEM_LOCKED(0x00040000), MEM_PRELOAD(0x00080000), MEM_DISCARDABLE(0x02000000), MEM_NOT_CACHED(0x04000000), MEM_NOT_PAGED(0x08000000),
	MEM_SHARED(0x10000000),

	EXECUTE(0x20000000), READ(0x40000000), WRITE(0x80000000);

	public static final SectionFlag[] VALUES = values();

	// ALIGN 1 -> 8192
	public static int alignShift(int flag) {
		return ((flag >> 20) & 15) - 1;
	}

	public static int alignBytes(int flag) {
		return 1 << (((flag >> 20) & 15) - 1);
	}

	public static int setAlignShift(int flag, int shift) {
		if (shift < 0 || shift > 13) throw new IllegalArgumentException("Unsupported align, should in 1-8192");
		return (flag & 0x00F00000) | ((shift + 1) << 20);
	}

	public final int mask;

	SectionFlag(int mask) {
		this.mask = mask;
	}

	public static EnumSet<SectionFlag> getFlags(int flags) {
		EnumSet<SectionFlag> flags1 = EnumSet.noneOf(SectionFlag.class);
		for (SectionFlag flag : VALUES) {
			if ((flag.mask & flags) != 0x0) {
				flags1.add(flag);
			}
		}
		return flags1;
	}
}
